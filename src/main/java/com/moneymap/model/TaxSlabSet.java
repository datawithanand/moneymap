package com.moneymap.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Instance-wide tax slab reference data — shared, NOT ownerId-scoped (Section 11A §d,
 * Section 17 §10.2/§12.5). Shipped seed data is editable, never hardcoded.
 */
public class TaxSlabSet {

    public static class TaxSlab {
        private BigDecimal upToAmount;   // null = no upper bound (top slab)
        private BigDecimal ratePercent;
        public TaxSlab() {}
        public TaxSlab(BigDecimal upToAmount, BigDecimal ratePercent) { this.upToAmount = upToAmount; this.ratePercent = ratePercent; }
        public BigDecimal getUpToAmount() { return upToAmount; }
        public void setUpToAmount(BigDecimal upToAmount) { this.upToAmount = upToAmount; }
        public BigDecimal getRatePercent() { return ratePercent; }
        public void setRatePercent(BigDecimal ratePercent) { this.ratePercent = ratePercent; }
    }

    private String id;
    private String financialYear;
    private String regime;              // OLD | NEW
    private BigDecimal standardDeduction;
    private BigDecimal rebate87AThreshold;
    private BigDecimal rebate87AMaxAmount;
    private BigDecimal cessPercent;
    private List<TaxSlab> slabs = new ArrayList<>();
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public BigDecimal getStandardDeduction() { return standardDeduction; }
    public void setStandardDeduction(BigDecimal standardDeduction) { this.standardDeduction = standardDeduction; }
    public BigDecimal getRebate87AThreshold() { return rebate87AThreshold; }
    public void setRebate87AThreshold(BigDecimal rebate87AThreshold) { this.rebate87AThreshold = rebate87AThreshold; }
    public BigDecimal getRebate87AMaxAmount() { return rebate87AMaxAmount; }
    public void setRebate87AMaxAmount(BigDecimal rebate87AMaxAmount) { this.rebate87AMaxAmount = rebate87AMaxAmount; }
    public BigDecimal getCessPercent() { return cessPercent; }
    public void setCessPercent(BigDecimal cessPercent) { this.cessPercent = cessPercent; }
    public List<TaxSlab> getSlabs() { return slabs; }
    public void setSlabs(List<TaxSlab> slabs) { this.slabs = slabs; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
