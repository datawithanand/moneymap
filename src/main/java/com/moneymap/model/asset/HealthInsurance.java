package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class HealthInsurance extends OwnedRecord {
    private String insurerName;
    private String policyNumber;
    private BigDecimal sumInsured;
    private BigDecimal premiumAmount;
    private String premiumFrequency;
    private String policyType;
    private String membersCovered;
    private LocalDate policyStartDate;
    private LocalDate policyEndDate;
    private LocalDate nextRenewalDate;
    private boolean is80DDeductible;
    private BigDecimal topUpSumInsured;
    private BigDecimal topUpDeductible;

    public String getInsurerName() { return insurerName; }
    public void setInsurerName(String insurerName) { this.insurerName = insurerName; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
    public BigDecimal getSumInsured() { return sumInsured; }
    public void setSumInsured(BigDecimal sumInsured) { this.sumInsured = sumInsured; }
    public BigDecimal getPremiumAmount() { return premiumAmount; }
    public void setPremiumAmount(BigDecimal premiumAmount) { this.premiumAmount = premiumAmount; }
    public String getPremiumFrequency() { return premiumFrequency; }
    public void setPremiumFrequency(String premiumFrequency) { this.premiumFrequency = premiumFrequency; }
    public String getPolicyType() { return policyType; }
    public void setPolicyType(String policyType) { this.policyType = policyType; }
    public String getMembersCovered() { return membersCovered; }
    public void setMembersCovered(String membersCovered) { this.membersCovered = membersCovered; }
    public LocalDate getPolicyStartDate() { return policyStartDate; }
    public void setPolicyStartDate(LocalDate policyStartDate) { this.policyStartDate = policyStartDate; }
    public LocalDate getPolicyEndDate() { return policyEndDate; }
    public void setPolicyEndDate(LocalDate policyEndDate) { this.policyEndDate = policyEndDate; }
    public LocalDate getNextRenewalDate() { return nextRenewalDate; }
    public void setNextRenewalDate(LocalDate nextRenewalDate) { this.nextRenewalDate = nextRenewalDate; }
    public boolean getIs80DDeductible() { return is80DDeductible; }
    public void setIs80DDeductible(boolean is80DDeductible) { this.is80DDeductible = is80DDeductible; }
    public BigDecimal getTopUpSumInsured() { return topUpSumInsured; }
    public void setTopUpSumInsured(BigDecimal topUpSumInsured) { this.topUpSumInsured = topUpSumInsured; }
    public BigDecimal getTopUpDeductible() { return topUpDeductible; }
    public void setTopUpDeductible(BigDecimal topUpDeductible) { this.topUpDeductible = topUpDeductible; }
}
