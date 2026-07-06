package com.moneymap.model.asset;

import java.math.BigDecimal;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class SavingsAccount extends OwnedRecord {
    private String bankName;
    private String accountNumber;
    private String accountType;
    private String ifscCode;
    private BigDecimal currentBalance;
    private BigDecimal interestRate;
    private String currency;
    private boolean isPrimary;

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getIfscCode() { return ifscCode; }
    public void setIfscCode(String ifscCode) { this.ifscCode = ifscCode; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public boolean getIsPrimary() { return isPrimary; }
    public void setIsPrimary(boolean isPrimary) { this.isPrimary = isPrimary; }
}
