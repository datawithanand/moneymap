package com.moneymap.service;

import com.moneymap.model.User;
import com.moneymap.model.asset.OwnedRecord;
import com.moneymap.model.asset.PfEmployerRecord;
import com.moneymap.module.Buckets.AllocationClass;
import com.moneymap.module.Buckets.Bucket;
import com.moneymap.module.ModuleDef;
import com.moneymap.module.ModuleRegistry;
import com.moneymap.repository.Db;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * The ONE place net worth, bucket, and allocation math is implemented (Section 12's Shared
 * Aggregation Architecture). Every dashboard and every export reads this service's output —
 * no view re-derives a financial figure.
 */
@Service
public class PortfolioAggregationService {

    public record ModuleSummary(String path, String displayName, Bucket bucket, int count, BigDecimal total) {}

    public static class PortfolioSummary {
        public BigDecimal totalAssets = BigDecimal.ZERO;
        public BigDecimal totalLiabilities = BigDecimal.ZERO;
        public BigDecimal netWorth = BigDecimal.ZERO;
        public final Map<Bucket, BigDecimal> buckets = new EnumMap<>(Bucket.class);
        public final Map<AllocationClass, BigDecimal> allocation = new EnumMap<>(AllocationClass.class);
        public final List<ModuleSummary> moduleSummaries = new ArrayList<>();

        public BigDecimal bucket(Bucket b) { return buckets.getOrDefault(b, BigDecimal.ZERO); }
        public BigDecimal allocationTotal() {
            return allocation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        /** Percentage share of one allocation class, for the donut/table views. */
        public BigDecimal allocationPercent(AllocationClass cls) {
            BigDecimal total = allocationTotal();
            if (total.signum() == 0) return BigDecimal.ZERO;
            return allocation.getOrDefault(cls, BigDecimal.ZERO)
                    .multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
        }
    }

    private final ModuleRegistry registry;
    private final Db db;

    public PortfolioAggregationService(ModuleRegistry registry, Db db) {
        this.registry = registry;
        this.db = db;
    }

    public PortfolioSummary aggregate(User owner) {
        return aggregate(owner, null);
    }

    /** tagFilter re-scopes every figure to one household member (Section 12 §25's family filter). */
    public PortfolioSummary aggregate(User owner, String tagFilter) {
        PortfolioSummary s = new PortfolioSummary();
        for (Bucket b : Bucket.values()) s.buckets.put(b, BigDecimal.ZERO);
        for (AllocationClass c : AllocationClass.values()) s.allocation.put(c, BigDecimal.ZERO);

        for (ModuleDef<?> def : registry.all()) {
            int count = 0;
            BigDecimal moduleTotal = BigDecimal.ZERO;
            for (Object record : def.repo.findWhere(r -> owner.getId().equals(((OwnedRecord) r).getOwnerId()))) {
                OwnedRecord owned = (OwnedRecord) record;
                if (tagFilter != null && !tagFilter.isBlank() && !tagFilter.equals(owned.getFamilyMemberTag()))
                    continue;
                count++;
                BigDecimal value = def.valueOf(record, owner);
                if (value == null) continue;
                moduleTotal = moduleTotal.add(value);
                applyValue(s, def.bucket, value, def.allocationOf(record, owner));
            }
            // PF parent/child rollup (Section 07 §6): value lives on employer records
            if (def.path.equals("pf")) {
                BigDecimal pfTotal = pfTotal(owner, tagFilter);
                moduleTotal = pfTotal;
                applyValue(s, Bucket.RETIREMENT, pfTotal, Map.of(AllocationClass.DEBT, BigDecimal.ONE));
            }
            if (count > 0 || moduleTotal.signum() != 0) {
                s.moduleSummaries.add(new ModuleSummary(def.path, def.displayName, def.bucket, count, moduleTotal));
            }
        }
        s.netWorth = s.totalAssets.subtract(s.totalLiabilities);
        return s;
    }

    private void applyValue(PortfolioSummary s, Bucket bucket, BigDecimal value,
                            Map<AllocationClass, BigDecimal> allocationFractions) {
        if (bucket == Bucket.LIABILITY) {
            s.totalLiabilities = s.totalLiabilities.add(value);
            s.buckets.merge(Bucket.LIABILITY, value, BigDecimal::add);
            return;   // liabilities never appear in the allocation view (Section 12)
        }
        if (bucket == Bucket.NONE) return;
        s.totalAssets = s.totalAssets.add(value);
        s.buckets.merge(bucket, value, BigDecimal::add);
        if (allocationFractions != null) {
            for (var e : allocationFractions.entrySet()) {
                s.allocation.merge(e.getKey(),
                        value.multiply(e.getValue()).setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
            }
        }
    }

    private BigDecimal pfTotal(User owner, String tagFilter) {
        return db.pfEmployerRecords.findWhere(r -> owner.getId().equals(r.getOwnerId())).stream()
                .filter(r -> tagFilter == null || tagFilter.isBlank() || tagFilter.equals(r.getFamilyMemberTag()))
                .map(PfEmployerRecord::getCurrentBalance)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
