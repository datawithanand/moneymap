package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class LicPolicy extends OwnedRecord {
    private String insurerName;
    private String policyName;
    private String policyNumber;
    private String policyType;
    private BigDecimal sumAssured;
    private BigDecimal premiumAmount;
    private String premiumFrequency;
    private LocalDate policyStartDate;
    private LocalDate maturityDate;
    private LocalDate nextPremiumDueDate;
    private BigDecimal surrenderValue;
    private BigDecimal bonusAccrued;
    private BigDecimal expectedMaturityValue;
    private BigDecimal ulipFundValue;
    private BigDecimal ulipEquityAllocationPercent;
    private BigDecimal ulipDebtAllocationPercent;
    private boolean is80CDeductible;

    public String getInsurerName() { return insurerName; }
    public void setInsurerName(String insurerName) { this.insurerName = insurerName; }
    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
    public String getPolicyType() { return policyType; }
    public void setPolicyType(String policyType) { this.policyType = policyType; }
    public BigDecimal getSumAssured() { return sumAssured; }
    public void setSumAssured(BigDecimal sumAssured) { this.sumAssured = sumAssured; }
    public BigDecimal getPremiumAmount() { return premiumAmount; }
    public void setPremiumAmount(BigDecimal premiumAmount) { this.premiumAmount = premiumAmount; }
    public String getPremiumFrequency() { return premiumFrequency; }
    public void setPremiumFrequency(String premiumFrequency) { this.premiumFrequency = premiumFrequency; }
    public LocalDate getPolicyStartDate() { return policyStartDate; }
    public void setPolicyStartDate(LocalDate policyStartDate) { this.policyStartDate = policyStartDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public LocalDate getNextPremiumDueDate() { return nextPremiumDueDate; }
    public void setNextPremiumDueDate(LocalDate nextPremiumDueDate) { this.nextPremiumDueDate = nextPremiumDueDate; }
    public BigDecimal getSurrenderValue() { return surrenderValue; }
    public void setSurrenderValue(BigDecimal surrenderValue) { this.surrenderValue = surrenderValue; }
    public BigDecimal getBonusAccrued() { return bonusAccrued; }
    public void setBonusAccrued(BigDecimal bonusAccrued) { this.bonusAccrued = bonusAccrued; }
    public BigDecimal getExpectedMaturityValue() { return expectedMaturityValue; }
    public void setExpectedMaturityValue(BigDecimal expectedMaturityValue) { this.expectedMaturityValue = expectedMaturityValue; }
    public BigDecimal getUlipFundValue() { return ulipFundValue; }
    public void setUlipFundValue(BigDecimal ulipFundValue) { this.ulipFundValue = ulipFundValue; }
    public BigDecimal getUlipEquityAllocationPercent() { return ulipEquityAllocationPercent; }
    public void setUlipEquityAllocationPercent(BigDecimal ulipEquityAllocationPercent) { this.ulipEquityAllocationPercent = ulipEquityAllocationPercent; }
    public BigDecimal getUlipDebtAllocationPercent() { return ulipDebtAllocationPercent; }
    public void setUlipDebtAllocationPercent(BigDecimal ulipDebtAllocationPercent) { this.ulipDebtAllocationPercent = ulipDebtAllocationPercent; }
    public boolean getIs80CDeductible() { return is80CDeductible; }
    public void setIs80CDeductible(boolean is80CDeductible) { this.is80CDeductible = is80CDeductible; }
}
