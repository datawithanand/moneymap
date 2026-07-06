package com.moneymap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneymap.model.HouseholdMember;
import com.moneymap.model.User;
import com.moneymap.model.asset.OwnedRecord;
import com.moneymap.model.asset.PfAccount;
import com.moneymap.model.asset.PfEmployerRecord;
import com.moneymap.module.ModuleDef;
import com.moneymap.module.ModuleRegistry;
import com.moneymap.repository.Db;
import com.moneymap.repository.HouseholdMemberRepository;
import com.moneymap.repository.json.JsonEntityRepository;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;

/**
 * JSON import (Section 13): Merge (with business-key duplicate detection — duplicates are
 * skipped and reported) or Replace (delete all existing data first). Validation failure
 * never partially imports.
 */
@Service
public class ImportService {

    public record ImportReport(int imported, int skipped, int errors, List<String> details) {}

    /** Business-meaning dedup keys per Section 13's table (technical ids are never matched). */
    private static final Map<String, Function<Map<String, Object>, String>> DEDUP_KEYS = new HashMap<>();
    static {
        DEDUP_KEYS.put("savingsAccounts", m -> key(m, "bankName", "accountNumber"));
        DEDUP_KEYS.put("fixedDeposits", m -> key(m, "fdNumber"));
        DEDUP_KEYS.put("recurringDeposits", m -> key(m, "accountNumber"));
        DEDUP_KEYS.put("ppfAccounts", m -> key(m, "accountNumber"));
        DEDUP_KEYS.put("npsAccounts", m -> key(m, "pran", "tier"));
        DEDUP_KEYS.put("governmentSchemes", m -> key(m, "accountNumber"));
        DEDUP_KEYS.put("termInsurance", m -> key(m, "policyNumber"));
        DEDUP_KEYS.put("healthInsurance", m -> key(m, "policyNumber"));
        DEDUP_KEYS.put("licEndowmentUlip", m -> key(m, "policyNumber"));
        DEDUP_KEYS.put("loans", m -> key(m, "loanAccountNumber"));
        DEDUP_KEYS.put("mutualFunds", m -> key(m, "folioNumber"));
        DEDUP_KEYS.put("equityHoldings", m -> key(m, "tickerSymbol", "exchange", "brokerName"));
        DEDUP_KEYS.put("bonds", m -> key(m, "isinCode"));
        DEDUP_KEYS.put("realEstate", m -> key(m, "address", "purchaseDate"));
        DEDUP_KEYS.put("goldHoldings", m -> key(m, "label", "goldType", "purchaseDate"));
        DEDUP_KEYS.put("physicalAssets", m -> key(m, "assetName", "purchaseDate"));
        // Cash in Hand, Crypto, ESOPs, Goals, Other Income, Salary Profiles: no natural key — always added as new
    }

    private static String key(Map<String, Object> m, String... fields) {
        StringBuilder sb = new StringBuilder();
        for (String f : fields) {
            Object v = m.get(f);
            if (v == null || String.valueOf(v).isBlank()) return null;   // blank key → dedup skipped (§13)
            sb.append(String.valueOf(v).toLowerCase().trim()).append("|");
        }
        return sb.toString();
    }

    private final ModuleRegistry registry;
    private final Db db;
    private final HouseholdMemberRepository householdMembers;
    private final ObjectMapper mapper;

    public ImportService(ModuleRegistry registry, Db db, HouseholdMemberRepository householdMembers,
                         ObjectMapper mapper) {
        this.registry = registry;
        this.db = db;
        this.householdMembers = householdMembers;
        this.mapper = mapper;
    }

    /** Validates and imports. Throws IllegalArgumentException on validation failure (zero records written). */
    @SuppressWarnings("unchecked")
    public ImportReport importJson(User owner, byte[] content, boolean replace) {
        JsonNode root;
        try {
            root = mapper.readTree(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("The file is not valid JSON.");
        }
        JsonNode version = root.get("exportVersion");
        if (version == null || !ExportService.EXPORT_VERSION.equals(version.asText())) {
            throw new IllegalArgumentException("Unrecognized or missing exportVersion — this file was not "
                    + "produced by a compatible MoneyMap version.");
        }
        JsonNode assets = root.get("assets");
        if (assets == null || !assets.isObject()) {
            throw new IllegalArgumentException("The file's structure does not match a MoneyMap export (missing 'assets').");
        }

        if (replace) {
            for (ModuleDef<?> def : registry.all()) {
                def.repo.deleteWhere(r -> owner.getId().equals(((OwnedRecord) r).getOwnerId()));
            }
            db.pfEmployerRecords.deleteWhere(r -> owner.getId().equals(r.getOwnerId()));
        }

        int imported = 0, skipped = 0, errors = 0;
        List<String> details = new ArrayList<>();

        // Household members first, so familyMemberTag values resolve
        JsonNode hh = root.get("householdMembers");
        if (hh != null && hh.isArray()) {
            Set<String> existing = new HashSet<>();
            householdMembers.findByOwnerId(owner.getId()).forEach(m -> existing.add(m.getLabel().toLowerCase()));
            for (JsonNode node : hh) {
                String label = node.path("label").asText(null);
                if (label == null || existing.contains(label.toLowerCase())) { skipped++; continue; }
                HouseholdMember m = new HouseholdMember();
                m.setOwnerId(owner.getId());
                m.setLabel(label);
                m.setDefaultEntry(node.path("defaultEntry").asBoolean(false) && label.equalsIgnoreCase("Self"));
                householdMembers.save(m);
                imported++;
            }
        }

        // PF UAN-parent exception (§13): attach imported employer records to an existing UAN parent
        Map<String, String> importedPfIdRemap = new HashMap<>();

        for (ModuleDef<?> def : registry.all()) {
            String jsonKey = camel(((JsonEntityRepository<?>) def.repo).collection());
            JsonNode arr = assets.get(jsonKey);
            if (arr == null || !arr.isArray()) continue;

            Set<String> existingKeys = new HashSet<>();
            Function<Map<String, Object>, String> keyFn = DEDUP_KEYS.get(jsonKey);
            if (keyFn != null && !replace) {
                for (Object rec : def.repo.findWhere(r -> owner.getId().equals(((OwnedRecord) r).getOwnerId()))) {
                    String k = keyFn.apply(beanToMap(rec));
                    if (k != null) existingKeys.add(k);
                }
            }

            for (JsonNode node : arr) {
                try {
                    OwnedRecord record = (OwnedRecord) mapper.treeToValue(node, def.type);
                    String importedId = record.getId();
                    record.setId(null);   // always a fresh technical id
                    record.setOwnerId(owner.getId());
                    if (keyFn != null && !replace) {
                        String k = keyFn.apply(beanToMap(record));
                        if (k != null && existingKeys.contains(k)) {
                            skipped++;
                            details.add("Skipped duplicate in " + def.displayName + " (matching business key).");
                            continue;
                        }
                        if (k != null) existingKeys.add(k);
                    }
                    if (record instanceof PfAccount pf) {
                        // UAN unique per owner: attach to existing parent instead of duplicating (§13)
                        Optional<PfAccount> existing = db.pfAccounts.findWhere(a ->
                                owner.getId().equals(a.getOwnerId()) && pf.getUan() != null
                                        && pf.getUan().equals(a.getUan())).stream().findFirst();
                        if (existing.isPresent()) {
                            if (importedId != null) importedPfIdRemap.put(importedId, existing.get().getId());
                            skipped++;
                            continue;
                        }
                    }
                    Object saved = ((JsonEntityRepository<Object>) (JsonEntityRepository<?>) def.repo).save(record);
                    if (record instanceof PfAccount && importedId != null) {
                        importedPfIdRemap.put(importedId, ((OwnedRecord) saved).getId());
                    }
                    imported++;
                } catch (Exception e) {
                    errors++;
                    details.add("Error in " + def.displayName + ": " + e.getMessage());
                }
            }
        }

        // PF employer child records (dedup key: pfMemberId)
        JsonNode pfChildren = assets.get("pfEmployerRecords");
        if (pfChildren != null && pfChildren.isArray()) {
            Set<String> existingMemberIds = new HashSet<>();
            if (!replace) db.pfEmployerRecords.findWhere(r -> owner.getId().equals(r.getOwnerId()))
                    .forEach(r -> { if (r.getPfMemberId() != null) existingMemberIds.add(r.getPfMemberId().toLowerCase()); });
            for (JsonNode node : pfChildren) {
                try {
                    PfEmployerRecord rec = mapper.treeToValue(node, PfEmployerRecord.class);
                    if (rec.getPfMemberId() != null && existingMemberIds.contains(rec.getPfMemberId().toLowerCase())) {
                        skipped++;
                        continue;
                    }
                    rec.setId(null);
                    rec.setOwnerId(owner.getId());
                    if (rec.getPfAccountId() != null && importedPfIdRemap.containsKey(rec.getPfAccountId())) {
                        rec.setPfAccountId(importedPfIdRemap.get(rec.getPfAccountId()));
                    }
                    db.pfEmployerRecords.save(rec);
                    imported++;
                } catch (Exception e) {
                    errors++;
                    details.add("Error in PF Employer Records: " + e.getMessage());
                }
            }
        }

        return new ImportReport(imported, skipped, errors, details);
    }

    private Map<String, Object> beanToMap(Object bean) {
        Map<String, Object> map = new HashMap<>();
        BeanWrapperImpl bw = new BeanWrapperImpl(bean);
        for (var pd : bw.getPropertyDescriptors()) {
            if (pd.getReadMethod() != null && !"class".equals(pd.getName())) {
                try {
                    map.put(pd.getName(), bw.getPropertyValue(pd.getName()));
                } catch (Exception ignored) {
                }
            }
        }
        return map;
    }

    private String camel(String snake) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char c : snake.toCharArray()) {
            if (c == '_' || c == '-') { up = true; continue; }
            sb.append(up ? Character.toUpperCase(c) : c);
            up = false;
        }
        return sb.toString();
    }
}
