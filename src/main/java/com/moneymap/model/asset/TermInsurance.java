package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class TermInsurance extends OwnedRecord {
    private String insurerName;
    private String policyNumber;
    private BigDecimal sumAssured;
    private BigDecimal premiumAmount;
    private String premiumFrequency;
    private LocalDate policyStartDate;
    private LocalDate policyEndDate;
    private LocalDate nextPremiumDueDate;
    private boolean is80CDeductible;

    public String getInsurerName() { return insurerName; }
    public void setInsurerName(String insurerName) { this.insurerName = insurerName; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
    public BigDecimal getSumAssured() { return sumAssured; }
    public void setSumAssured(BigDecimal sumAssured) { this.sumAssured = sumAssured; }
    public BigDecimal getPremiumAmount() { return premiumAmount; }
    public void setPremiumAmount(BigDecimal premiumAmount) { this.premiumAmount = premiumAmount; }
    public String getPremiumFrequency() { return premiumFrequency; }
    public void setPremiumFrequency(String premiumFrequency) { this.premiumFrequency = premiumFrequency; }
    public LocalDate getPolicyStartDate() { return policyStartDate; }
    public void setPolicyStartDate(LocalDate policyStartDate) { this.policyStartDate = policyStartDate; }
    public LocalDate getPolicyEndDate() { return policyEndDate; }
    public void setPolicyEndDate(LocalDate policyEndDate) { this.policyEndDate = policyEndDate; }
    public LocalDate getNextPremiumDueDate() { return nextPremiumDueDate; }
    public void setNextPremiumDueDate(LocalDate nextPremiumDueDate) { this.nextPremiumDueDate = nextPremiumDueDate; }
    public boolean getIs80CDeductible() { return is80CDeductible; }
    public void setIs80CDeductible(boolean is80CDeductible) { this.is80CDeductible = is80CDeductible; }
}
