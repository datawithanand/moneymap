package com.moneymap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneymap.model.FundMaster;
import com.moneymap.repository.Db;
import com.moneymap.repository.GlobalSettingsRepository;
import com.moneymap.repository.JsonCollectionStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private static final String BASE_URL = "https://api.mfapi.in";
    private static final DateTimeFormatter MFAPI_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH);

    private final Db db;
    private final JsonCollectionStore store;
    private final GlobalSettingsRepository globalSettings;
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FundMasterService(Db db, JsonCollectionStore store, GlobalSettingsRepository globalSettings,
                             ObjectMapper mapper) {
        this.db = db;
        this.store = store;
        this.globalSettings = globalSettings;
        this.mapper = mapper;
    }

    /**
     * Bulk-syncs the scheme name/code list from api.mfapi.in/mf. Existing rows are updated
     * (name only); previously-cached enrichment (category, NAV, etc.) is preserved. Schemes
     * no longer present upstream are NOT removed, so a user's already-linked schemeCode never
     * silently breaks. Returns the number of schemes in the upstream response.
     */
    public int syncSchemeList() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/mf"))
                .timeout(Duration.ofSeconds(60))
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

    private JsonNode sendAndParse(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach api.mfapi.in: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Request to api.mfapi.in was interrupted.", e);
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("api.mfapi.in returned HTTP " + response.statusCode());
        }
        try {
            return mapper.readTree(response.body());
        } catch (IOException e) {
            throw new IllegalStateException("Invalid JSON response from api.mfapi.in.", e);
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
