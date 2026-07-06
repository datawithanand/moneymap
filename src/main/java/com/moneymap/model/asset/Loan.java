package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class Loan extends OwnedRecord {
    private String loanType;
    private String lenderName;
    private String loanAccountNumber;
    private BigDecimal principalAmount;
    private BigDecimal outstandingBalance;
    private BigDecimal interestRate;
    private BigDecimal emiAmount;
    private Integer tenureMonths;
    private LocalDate startDate;
    private LocalDate expectedClosureDate;
    private String linkedRealEstatePropertyId;
    private BigDecimal creditLimit;
    private BigDecimal minimumAmountDue;
    private LocalDate paymentDueDate;

    public String getLoanType() { return loanType; }
    public void setLoanType(String loanType) { this.loanType = loanType; }
    public String getLenderName() { return lenderName; }
    public void setLenderName(String lenderName) { this.lenderName = lenderName; }
    public String getLoanAccountNumber() { return loanAccountNumber; }
    public void setLoanAccountNumber(String loanAccountNumber) { this.loanAccountNumber = loanAccountNumber; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public void setOutstandingBalance(BigDecimal outstandingBalance) { this.outstandingBalance = outstandingBalance; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public BigDecimal getEmiAmount() { return emiAmount; }
    public void setEmiAmount(BigDecimal emiAmount) { this.emiAmount = emiAmount; }
    public Integer getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(Integer tenureMonths) { this.tenureMonths = tenureMonths; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getExpectedClosureDate() { return expectedClosureDate; }
    public void setExpectedClosureDate(LocalDate expectedClosureDate) { this.expectedClosureDate = expectedClosureDate; }
    public String getLinkedRealEstatePropertyId() { return linkedRealEstatePropertyId; }
    public void setLinkedRealEstatePropertyId(String linkedRealEstatePropertyId) { this.linkedRealEstatePropertyId = linkedRealEstatePropertyId; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    public BigDecimal getMinimumAmountDue() { return minimumAmountDue; }
    public void setMinimumAmountDue(BigDecimal minimumAmountDue) { this.minimumAmountDue = minimumAmountDue; }
    public LocalDate getPaymentDueDate() { return paymentDueDate; }
    public void setPaymentDueDate(LocalDate paymentDueDate) { this.paymentDueDate = paymentDueDate; }
}
