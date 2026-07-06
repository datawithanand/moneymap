package com.moneymap.model.asset;

import java.math.BigDecimal;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class NpsAccount extends OwnedRecord {
    private String pran;
    private String tier;
    private BigDecimal currentCorpus;
    private BigDecimal equityAllocationPercent;
    private BigDecimal corporateBondAllocationPercent;
    private BigDecimal govSecuritiesAllocationPercent;
    private String pensionFundManager;

    public String getPran() { return pran; }
    public void setPran(String pran) { this.pran = pran; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public BigDecimal getCurrentCorpus() { return currentCorpus; }
    public void setCurrentCorpus(BigDecimal currentCorpus) { this.currentCorpus = currentCorpus; }
    public BigDecimal getEquityAllocationPercent() { return equityAllocationPercent; }
    public void setEquityAllocationPercent(BigDecimal equityAllocationPercent) { this.equityAllocationPercent = equityAllocationPercent; }
    public BigDecimal getCorporateBondAllocationPercent() { return corporateBondAllocationPercent; }
    public void setCorporateBondAllocationPercent(BigDecimal corporateBondAllocationPercent) { this.corporateBondAllocationPercent = corporateBondAllocationPercent; }
    public BigDecimal getGovSecuritiesAllocationPercent() { return govSecuritiesAllocationPercent; }
    public void setGovSecuritiesAllocationPercent(BigDecimal govSecuritiesAllocationPercent) { this.govSecuritiesAllocationPercent = govSecuritiesAllocationPercent; }
    public String getPensionFundManager() { return pensionFundManager; }
    public void setPensionFundManager(String pensionFundManager) { this.pensionFundManager = pensionFundManager; }
}
