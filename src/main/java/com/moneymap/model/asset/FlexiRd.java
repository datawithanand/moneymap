package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** iWISH / Flexi RD — irregular deposit ledger (Section 05 §5, Section 17 §3.5). */
public class FlexiRd extends OwnedRecord {

    public static class FlexiDeposit {
        private LocalDate depositDate;
        private BigDecimal amount;
        public LocalDate getDepositDate() { return depositDate; }
        public void setDepositDate(LocalDate depositDate) { this.depositDate = depositDate; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    private String bankName;
    private String accountNumber;
    private LocalDate targetMaturityDate;
    private BigDecimal interestRate;
    private List<FlexiDeposit> deposits = new ArrayList<>();
    private BigDecimal currentValueOverride;
    private String currency = "INR";

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public LocalDate getTargetMaturityDate() { return targetMaturityDate; }
    public void setTargetMaturityDate(LocalDate targetMaturityDate) { this.targetMaturityDate = targetMaturityDate; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public List<FlexiDeposit> getDeposits() { return deposits; }
    public void setDeposits(List<FlexiDeposit> deposits) { this.deposits = deposits; }
    public BigDecimal getCurrentValueOverride() { return currentValueOverride; }
    public void setCurrentValueOverride(BigDecimal currentValueOverride) { this.currentValueOverride = currentValueOverride; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal totalDeposited() {
        return deposits.stream().map(FlexiDeposit::getAmount)
                .filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
