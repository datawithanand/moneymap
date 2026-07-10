package com.moneymap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneymap.model.FundMaster;
import com.moneymap.repository.Db;
import com.moneymap.repository.GlobalSettingsRepository;
import com.moneymap.repository.JsonCollectionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Mutual fund scheme reference-data cache, sourced from the free public api.mfapi.in API
 * (Calculators/Mutual Funds fund-picker feature). Bulk sync (name + scheme code only) is
 * admin-triggered on demand (Section: Fund Master); category/fund-house/NAV are enriched
 * lazily, one scheme at a time, whenever a user's fund search resolves a specific scheme —
 * fetching category for all ~30,000 schemes up front would mean ~30,000 individual calls to
 * a free, unauthenticated, no-SLA API, which this deliberately avoids.
 */
@Service
public class FundMasterService {

    private static final Logger log = LoggerFactory.getLogger(FundMasterService.class);

    private static final String BASE_URL = "https://api.mfapi.in";
    private static final DateTimeFormatter MFAPI_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);
    private static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofSeconds(2);

    private final Db db;
    private final JsonCollectionStore store;
    private final GlobalSettingsRepository globalSettings;
    private final ObjectMapper mapper;
    private final Duration connectTimeout;
    private final Duration requestTimeout;
    private final HttpClient httpClient;

    public FundMasterService(Db db, JsonCollectionStore store, GlobalSettingsRepository globalSettings,
                             ObjectMapper mapper,
                             @Value("${moneymap.fund-master.connect-timeout-seconds:10}") int connectTimeoutSeconds,
                             @Value("${moneymap.fund-master.request-timeout-seconds:60}") int requestTimeoutSeconds) {
        this.db = db;
        this.store = store;
        this.globalSettings = globalSettings;
        this.mapper = mapper;
        this.connectTimeout = Duration.ofSeconds(connectTimeoutSeconds);
        this.requestTimeout = Duration.ofSeconds(requestTimeoutSeconds);
        this.httpClient = HttpClient.newBuilder().connectTimeout(this.connectTimeout).build();
    }

    /**
     * Bulk-syncs the scheme name/code list from api.mfapi.in/mf. Existing rows are updated
     * (name only); previously-cached enrichment (category, NAV, etc.) is preserved. Schemes
     * no longer present upstream are NOT removed, so a user's already-linked schemeCode never
     * silently breaks. Returns the number of schemes in the upstream response.
     */
    public int syncSchemeList() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/mf"))
                .timeout(requestTimeout)
                .GET()
                .build();
        JsonNode arr = sendAndParse(request);
        if (!arr.isArray()) {
            throw new IllegalStateException("Unexpected response shape from api.mfapi.in/mf (expected a JSON array).");
        }

        Map<Integer, String> incoming = new LinkedHashMap<>();
        for (JsonNode node : arr) {
            int code = node.path("schemeCode").asInt(0);
            String name = node.path("schemeName").asText(null);
            if (code != 0 && name != null && !name.isBlank()) {
                incoming.put(code, name);
            }
        }

        int total = store.mutate("fund_master", FundMaster.class, list -> {
            Map<Integer, FundMaster> byCode = new HashMap<>();
            for (FundMaster fm : list) {
                if (fm.getSchemeCode() != null) byCode.put(fm.getSchemeCode(), fm);
            }
            for (Map.Entry<Integer, String> entry : incoming.entrySet()) {
                FundMaster existing = byCode.get(entry.getKey());
                if (existing != null) {
                    existing.setSchemeName(entry.getValue());
                } else {
                    FundMaster fm = new FundMaster();
                    fm.setId(UUID.randomUUID().toString());
                    fm.setSchemeCode(entry.getKey());
                    fm.setSchemeName(entry.getValue());
                    list.add(fm);
                }
            }
            return incoming.size();
        });

        globalSettings.update(s -> {
            s.setFundMasterLastSyncedAt(Instant.now());
            s.setFundMasterSchemeCount(total);
            return s;
        });
        return total;
    }

    /**
     * Fetches one scheme's live detail (fund house, category, latest NAV/date) and caches it
     * onto the matching FundMaster row. This is the point where category gets enriched.
     */
    public Optional<FundMaster> fetchAndCacheDetail(int schemeCode) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/mf/" + schemeCode))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        JsonNode root = sendAndParse(request);
        JsonNode meta = root.path("meta");
        JsonNode dataArr = root.path("data");
        if (meta.isMissingNode() || !dataArr.isArray() || dataArr.isEmpty()) {
            return Optional.empty();
        }

        String schemeName = textOrNull(meta, "scheme_name");
        String fundHouse = textOrNull(meta, "fund_house");
        String rawCategory = textOrNull(meta, "scheme_category");
        String isinGrowth = textOrNull(meta, "isin_growth");
        String isinDivReinvestment = textOrNull(meta, "isin_div_reinvestment");

        JsonNode latest = dataArr.get(0);
        BigDecimal nav = parseNav(latest.path("nav").asText(null));
        LocalDate navDate = parseMfapiDate(latest.path("date").asText(null));

        List<FundMaster> existing = db.fundMaster.findWhere(fm -> Integer.valueOf(schemeCode).equals(fm.getSchemeCode()));
        FundMaster fm = existing.isEmpty() ? new FundMaster() : existing.get(0);
        fm.setSchemeCode(schemeCode);
        if (schemeName != null) fm.setSchemeName(schemeName);
        fm.setFundHouse(fundHouse);
        fm.setIsinGrowth(isinGrowth);
        fm.setIsinDivReinvestment(isinDivReinvestment);
        fm.setRawCategory(rawCategory);
        fm.setCategoryBucket(deriveCategoryBucket(rawCategory));
        fm.setLatestNav(nav);
        fm.setNavAsOfDate(navDate);
        fm.setLastEnrichedAt(Instant.now());
        return Optional.of(db.fundMaster.save(fm));
    }

    /** Local-cache-only search (no external call) — fast typeahead for the fund picker. */
    public List<FundMaster> search(String query, String categoryBucket, int limit) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        return db.fundMaster.findWhere(fm -> {
            if (categoryBucket != null && !categoryBucket.isBlank()
                    && !categoryBucket.equalsIgnoreCase(fm.getCategoryBucket())) {
                return false;
            }
            if (q.isEmpty()) return true;
            return fm.getSchemeName() != null && fm.getSchemeName().toLowerCase(Locale.ROOT).contains(q);
        }).stream().limit(limit).toList();
    }

    /**
     * Sends the request with up to MAX_ATTEMPTS tries, retrying only transient network failures
     * (connect timeout, connection refused, general I/O errors) with exponential backoff. A
     * non-200 HTTP response or a malformed body is NOT retried — those are deterministic and a
     * retry won't help. Every raw exception is translated into a clear, user-facing message; the
     * technical detail is always logged separately for diagnosis.
     */
    private JsonNode sendAndParse(HttpRequest request) {
        Exception lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    log.warn("[FundMaster] {} returned HTTP {}", request.uri(), response.statusCode());
                    throw new IllegalStateException(userMessageForStatus(response.statusCode()));
                }
                try {
                    return mapper.readTree(response.body());
                } catch (IOException e) {
                    log.error("[FundMaster] Malformed JSON from {}: {}", request.uri(), e.getMessage());
                    throw new IllegalStateException("The fund data service returned an unexpected response format. "
                            + "This usually resolves itself — try again shortly.");
                }
            } catch (HttpTimeoutException e) {
                lastFailure = e;
                log.warn("[FundMaster] Attempt {}/{} timed out reaching {}: {}", attempt, MAX_ATTEMPTS, request.uri(), e.getMessage());
            } catch (ConnectException e) {
                lastFailure = e;
                log.warn("[FundMaster] Attempt {}/{} could not connect to {}: {}", attempt, MAX_ATTEMPTS, request.uri(), e.getMessage());
            } catch (IOException e) {
                lastFailure = e;
                log.warn("[FundMaster] Attempt {}/{} network error reaching {}: {}", attempt, MAX_ATTEMPTS, request.uri(), e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("The request was interrupted. Please try again.", e);
            }
            if (attempt < MAX_ATTEMPTS) {
                sleepBackoff(attempt);
            }
        }
        log.error("[FundMaster] Giving up after {} attempts reaching {}", MAX_ATTEMPTS, request.uri(), lastFailure);
        throw new IllegalStateException("Could not reach the mutual fund data service (api.mfapi.in) after "
                + MAX_ATTEMPTS + " attempts. This is usually a temporary network issue on this server — "
                + "check its internet connectivity and try again in a few minutes. "
                + "Any previously synced fund list remains available and usable in the meantime.", lastFailure);
    }

    private static String userMessageForStatus(int statusCode) {
        if (statusCode == 429) {
            return "The mutual fund data service is rate-limiting requests right now — please wait a few minutes and try again.";
        }
        if (statusCode >= 500) {
            return "The mutual fund data service (api.mfapi.in) is temporarily unavailable (HTTP " + statusCode
                    + "). This is on their end — please try again shortly.";
        }
        return "The mutual fund data service returned an unexpected error (HTTP " + statusCode + ").";
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(INITIAL_BACKOFF.toMillis() * (1L << (attempt - 1)));   // 2s, 4s, 8s...
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull()) ? null : v.asText(null);
    }

    private static String deriveCategoryBucket(String rawCategory) {
        if (rawCategory == null) return null;
        String c = rawCategory.toLowerCase(Locale.ROOT);
        if (c.contains("hybrid")) return "HYBRID";
        if (c.contains("equity")) return "EQUITY";
        if (c.contains("debt")) return "DEBT";
        return "OTHER";
    }

    private static BigDecimal parseNav(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseMfapiDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDate.parse(raw.trim(), MFAPI_DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
