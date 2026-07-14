package com.moneymap.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Per-user singleton — the few inputs the Financial Health Score needs that aren't tracked elsewhere. */
public class FinancialHealthInputs {
    private String id;
    private String ownerId;
    private BigDecimal monthlyExpense;
    private BigDecimal monthlySavings;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public BigDecimal getMonthlyExpense() { return monthlyExpense; }
    public void setMonthlyExpense(BigDecimal monthlyExpense) { this.monthlyExpense = monthlyExpense; }
    public BigDecimal getMonthlySavings() { return monthlySavings; }
    public void setMonthlySavings(BigDecimal monthlySavings) { this.monthlySavings = monthlySavings; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
