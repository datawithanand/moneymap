package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class GovernmentScheme extends OwnedRecord {
    private String schemeType;
    private String schemeName;
    private String accountNumber;
    private String bankOrPostOffice;
    private LocalDate openingDate;
    private BigDecimal currentBalance;
    private BigDecimal yearlyContribution;
    private BigDecimal interestRate;
    private LocalDate maturityDate;

    public String getSchemeType() { return schemeType; }
    public void setSchemeType(String schemeType) { this.schemeType = schemeType; }
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getBankOrPostOffice() { return bankOrPostOffice; }
    public void setBankOrPostOffice(String bankOrPostOffice) { this.bankOrPostOffice = bankOrPostOffice; }
    public LocalDate getOpeningDate() { return openingDate; }
    public void setOpeningDate(LocalDate openingDate) { this.openingDate = openingDate; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    public BigDecimal getYearlyContribution() { return yearlyContribution; }
    public void setYearlyContribution(BigDecimal yearlyContribution) { this.yearlyContribution = yearlyContribution; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
}
