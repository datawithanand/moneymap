package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class RecurringDeposit extends OwnedRecord {
    private String bankName;
    private String accountNumber;
    private BigDecimal monthlyInstallmentAmount;
    private LocalDate startDate;
    private Integer tenureMonths;
    private BigDecimal interestRate;
    private String currency;

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public BigDecimal getMonthlyInstallmentAmount() { return monthlyInstallmentAmount; }
    public void setMonthlyInstallmentAmount(BigDecimal monthlyInstallmentAmount) { this.monthlyInstallmentAmount = monthlyInstallmentAmount; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public Integer getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(Integer tenureMonths) { this.tenureMonths = tenureMonths; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
