package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class PfEmployerRecord extends OwnedRecord {
    private String pfAccountId;
    private String employerName;
    private String pfMemberId;
    private LocalDate employmentStartDate;
    private LocalDate employmentEndDate;
    private BigDecimal employeeContributionPerMonth;
    private BigDecimal employerEpfContributionPerMonth;
    private BigDecimal employerEpsContributionPerMonth;
    private BigDecimal vpfContributionPerMonth;
    private BigDecimal currentBalance;
    private BigDecimal interestRate;
    private String status;

    public String getPfAccountId() { return pfAccountId; }
    public void setPfAccountId(String pfAccountId) { this.pfAccountId = pfAccountId; }
    public String getEmployerName() { return employerName; }
    public void setEmployerName(String employerName) { this.employerName = employerName; }
    public String getPfMemberId() { return pfMemberId; }
    public void setPfMemberId(String pfMemberId) { this.pfMemberId = pfMemberId; }
    public LocalDate getEmploymentStartDate() { return employmentStartDate; }
    public void setEmploymentStartDate(LocalDate employmentStartDate) { this.employmentStartDate = employmentStartDate; }
    public LocalDate getEmploymentEndDate() { return employmentEndDate; }
    public void setEmploymentEndDate(LocalDate employmentEndDate) { this.employmentEndDate = employmentEndDate; }
    public BigDecimal getEmployeeContributionPerMonth() { return employeeContributionPerMonth; }
    public void setEmployeeContributionPerMonth(BigDecimal employeeContributionPerMonth) { this.employeeContributionPerMonth = employeeContributionPerMonth; }
    public BigDecimal getEmployerEpfContributionPerMonth() { return employerEpfContributionPerMonth; }
    public void setEmployerEpfContributionPerMonth(BigDecimal employerEpfContributionPerMonth) { this.employerEpfContributionPerMonth = employerEpfContributionPerMonth; }
    public BigDecimal getEmployerEpsContributionPerMonth() { return employerEpsContributionPerMonth; }
    public void setEmployerEpsContributionPerMonth(BigDecimal employerEpsContributionPerMonth) { this.employerEpsContributionPerMonth = employerEpsContributionPerMonth; }
    public BigDecimal getVpfContributionPerMonth() { return vpfContributionPerMonth; }
    public void setVpfContributionPerMonth(BigDecimal vpfContributionPerMonth) { this.vpfContributionPerMonth = vpfContributionPerMonth; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public void setCurrentBalance(BigDecimal currentBalance) { this.currentBalance = currentBalance; }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
