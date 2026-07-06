package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class FixedDeposit extends OwnedRecord {
    private String bankName;
    private String fdNumber;
    private BigDecimal principalAmount;
    private LocalDate startDate;
    private LocalDate maturityDate;
    private BigDecimal interestRate;
    private String payoutType;
    private boolean isTaxSaverFD;
    private String payoutAccountId;
    private String currency;

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getFdNumber() { return fdNumber; }
    public void setFdNumber(String fdNumber) { this.fdNumber = fdNumber; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public String getPayoutType() { return payoutType; }
    public void setPayoutType(String payoutType) { this.payoutType = payoutType; }
    public boolean getIsTaxSaverFD() { return isTaxSaverFD; }
    public void setIsTaxSaverFD(boolean isTaxSaverFD) { this.isTaxSaverFD = isTaxSaverFD; }
    public String getPayoutAccountId() { return payoutAccountId; }
    public void setPayoutAccountId(String payoutAccountId) { this.payoutAccountId = payoutAccountId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
