package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class Gratuity extends OwnedRecord {
    private String employerName;
    private LocalDate dateOfJoining;
    private BigDecimal lastDrawnSalary;
    private BigDecimal yearsOfService;
    private BigDecimal expectedGratuityAmount;
    private boolean includeInNetWorth = true;

    public String getEmployerName() { return employerName; }
    public void setEmployerName(String employerName) { this.employerName = employerName; }
    public LocalDate getDateOfJoining() { return dateOfJoining; }
    public void setDateOfJoining(LocalDate dateOfJoining) { this.dateOfJoining = dateOfJoining; }
    public BigDecimal getLastDrawnSalary() { return lastDrawnSalary; }
    public void setLastDrawnSalary(BigDecimal lastDrawnSalary) { this.lastDrawnSalary = lastDrawnSalary; }
    public BigDecimal getYearsOfService() { return yearsOfService; }
    public void setYearsOfService(BigDecimal yearsOfService) { this.yearsOfService = yearsOfService; }
    public BigDecimal getExpectedGratuityAmount() { return expectedGratuityAmount; }
    public void setExpectedGratuityAmount(BigDecimal expectedGratuityAmount) { this.expectedGratuityAmount = expectedGratuityAmount; }
    public boolean getIncludeInNetWorth() { return includeInNetWorth; }
    public void setIncludeInNetWorth(boolean includeInNetWorth) { this.includeInNetWorth = includeInNetWorth; }
}
