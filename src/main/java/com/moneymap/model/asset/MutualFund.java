package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class MutualFund extends OwnedRecord {
    private String folioNumber;
    private String fundName;
    private String amcName;
    private String category;
    private String investmentType;
    private BigDecimal unitsHeld;
    private BigDecimal averageNavPerUnit;
    private BigDecimal currentNavPerUnit;
    private LocalDate navAsOfDate;
    private BigDecimal sipAmount;
    private Integer sipDate;
    private String sipStatus;
    private boolean isElss;
    private LocalDate elssLockInExpiryDate;
    private BigDecimal equityAllocationPercent;
    private String currency;

    public String getFolioNumber() { return folioNumber; }
    public void setFolioNumber(String folioNumber) { this.folioNumber = folioNumber; }
    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }
    public String getAmcName() { return amcName; }
    public void setAmcName(String amcName) { this.amcName = amcName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getInvestmentType() { return investmentType; }
    public void setInvestmentType(String investmentType) { this.investmentType = investmentType; }
    public BigDecimal getUnitsHeld() { return unitsHeld; }
    public void setUnitsHeld(BigDecimal unitsHeld) { this.unitsHeld = unitsHeld; }
    public BigDecimal getAverageNavPerUnit() { return averageNavPerUnit; }
    public void setAverageNavPerUnit(BigDecimal averageNavPerUnit) { this.averageNavPerUnit = averageNavPerUnit; }
    public BigDecimal getCurrentNavPerUnit() { return currentNavPerUnit; }
    public void setCurrentNavPerUnit(BigDecimal currentNavPerUnit) { this.currentNavPerUnit = currentNavPerUnit; }
    public LocalDate getNavAsOfDate() { return navAsOfDate; }
    public void setNavAsOfDate(LocalDate navAsOfDate) { this.navAsOfDate = navAsOfDate; }
    public BigDecimal getSipAmount() { return sipAmount; }
    public void setSipAmount(BigDecimal sipAmount) { this.sipAmount = sipAmount; }
    public Integer getSipDate() { return sipDate; }
    public void setSipDate(Integer sipDate) { this.sipDate = sipDate; }
    public String getSipStatus() { return sipStatus; }
    public void setSipStatus(String sipStatus) { this.sipStatus = sipStatus; }
    public boolean getIsElss() { return isElss; }
    public void setIsElss(boolean isElss) { this.isElss = isElss; }
    public LocalDate getElssLockInExpiryDate() { return elssLockInExpiryDate; }
    public void setElssLockInExpiryDate(LocalDate elssLockInExpiryDate) { this.elssLockInExpiryDate = elssLockInExpiryDate; }
    public BigDecimal getEquityAllocationPercent() { return equityAllocationPercent; }
    public void setEquityAllocationPercent(BigDecimal equityAllocationPercent) { this.equityAllocationPercent = equityAllocationPercent; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
