package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Salary profile with dynamic earning/deduction components (Section 11A, Section 17 §10.1). */
public class SalaryProfile extends OwnedRecord {

    public static class PayComponent {
        private String label;
        private BigDecimal amount;
        public PayComponent() {}
        public PayComponent(String label, BigDecimal amount) { this.label = label; this.amount = amount; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
    }

    private String employerName;
    private String designation;
    private LocalDate effectiveDate;
    private String financialYear;   // e.g. "2026-27"
    private String regime;          // OLD | NEW
    private List<PayComponent> earnings = new ArrayList<>();
    private List<PayComponent> deductions = new ArrayList<>();

    public String getEmployerName() { return employerName; }
    public void setEmployerName(String employerName) { this.employerName = employerName; }
    public String getDesignation() { return designation; }
    public void setDesignation(String designation) { this.designation = designation; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }
    public String getFinancialYear() { return financialYear; }
    public void setFinancialYear(String financialYear) { this.financialYear = financialYear; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public List<PayComponent> getEarnings() { return earnings; }
    public void setEarnings(List<PayComponent> earnings) { this.earnings = earnings; }
    public List<PayComponent> getDeductions() { return deductions; }
    public void setDeductions(List<PayComponent> deductions) { this.deductions = deductions; }

    public BigDecimal grossMonthly() {
        return earnings.stream().map(PayComponent::getAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    public BigDecimal deductionsMonthly() {
        return deductions.stream().map(PayComponent::getAmount).filter(a -> a != null).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
