package com.moneymap.service;

import com.moneymap.model.TaxSlabSet;
import com.moneymap.repository.Db;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Tax slab engine (Section 11A §24). Slab sets are shared instance-wide reference data,
 * seeded with FY 2025-26 rules — explicitly editable, never hardcoded in logic.
 * Marginal relief is a v2 item (clean threshold cutoff in v1).
 */
@Service
public class TaxService {

    private final Db db;

    public TaxService(Db db) {
        this.db = db;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultSlabs() {
        if (!db.taxSlabSets.findAll().isEmpty()) return;

        TaxSlabSet newRegime = new TaxSlabSet();
        newRegime.setFinancialYear("2025-26");
        newRegime.setRegime("NEW");
        newRegime.setStandardDeduction(bd(75000));
        newRegime.setRebate87AThreshold(bd(1200000));
        newRegime.setRebate87AMaxAmount(bd(60000));
        newRegime.setCessPercent(bd(4));
        newRegime.setSlabs(List.of(
                new TaxSlabSet.TaxSlab(bd(400000), BigDecimal.ZERO),
                new TaxSlabSet.TaxSlab(bd(800000), bd(5)),
                new TaxSlabSet.TaxSlab(bd(1200000), bd(10)),
                new TaxSlabSet.TaxSlab(bd(1600000), bd(15)),
                new TaxSlabSet.TaxSlab(bd(2000000), bd(20)),
                new TaxSlabSet.TaxSlab(bd(2400000), bd(25)),
                new TaxSlabSet.TaxSlab(null, bd(30))));
        db.taxSlabSets.save(newRegime);

        TaxSlabSet oldRegime = new TaxSlabSet();
        oldRegime.setFinancialYear("2025-26");
        oldRegime.setRegime("OLD");
        oldRegime.setStandardDeduction(bd(50000));
        oldRegime.setRebate87AThreshold(bd(500000));
        oldRegime.setRebate87AMaxAmount(bd(12500));
        oldRegime.setCessPercent(bd(4));
        oldRegime.setSlabs(List.of(
                new TaxSlabSet.TaxSlab(bd(250000), BigDecimal.ZERO),
                new TaxSlabSet.TaxSlab(bd(500000), bd(5)),
                new TaxSlabSet.TaxSlab(bd(1000000), bd(20)),
                new TaxSlabSet.TaxSlab(null, bd(30))));
        db.taxSlabSets.save(oldRegime);
    }

    public Optional<TaxSlabSet> findSet(String financialYear, String regime) {
        return db.taxSlabSets.findWhere(s ->
                s.getFinancialYear().equals(financialYear) && s.getRegime().equals(regime)).stream().findFirst();
    }

    /** Computes annual tax on taxable income against a slab set (slab tax → 87A rebate → cess). */
    public BigDecimal computeTax(BigDecimal taxableIncome, TaxSlabSet set) {
        if (taxableIncome == null || taxableIncome.signum() <= 0 || set == null) return BigDecimal.ZERO;
        BigDecimal tax = BigDecimal.ZERO;
        BigDecimal lower = BigDecimal.ZERO;
        for (TaxSlabSet.TaxSlab slab : set.getSlabs()) {
            BigDecimal upper = slab.getUpToAmount() == null ? taxableIncome : slab.getUpToAmount().min(taxableIncome);
            if (upper.compareTo(lower) > 0) {
                tax = tax.add(upper.subtract(lower)
                        .multiply(slab.getRatePercent()).divide(bd(100), 2, RoundingMode.HALF_UP));
            }
            if (slab.getUpToAmount() == null || taxableIncome.compareTo(slab.getUpToAmount()) <= 0) break;
            lower = slab.getUpToAmount();
        }
        // Section 87A rebate — clean threshold cutoff (marginal relief deferred to v2)
        if (set.getRebate87AThreshold() != null && taxableIncome.compareTo(set.getRebate87AThreshold()) <= 0) {
            tax = tax.subtract(set.getRebate87AMaxAmount() == null ? tax : tax.min(set.getRebate87AMaxAmount()));
            if (tax.signum() < 0) tax = BigDecimal.ZERO;
        }
        if (set.getCessPercent() != null) {
            tax = tax.add(tax.multiply(set.getCessPercent()).divide(bd(100), 2, RoundingMode.HALF_UP));
        }
        return tax.setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal bd(long v) { return BigDecimal.valueOf(v); }
}
