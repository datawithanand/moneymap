package com.moneymap.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Per-user target allocation, singleton per owner (Section 12 §27, Section 17 §11.2). */
public class AllocationTarget {
    private String id;
    private String ownerId;
    private BigDecimal equityTargetPercent;
    private BigDecimal debtTargetPercent;
    private BigDecimal goldTargetPercent;
    private BigDecimal realEstateTargetPercent;
    private BigDecimal cashTargetPercent;
    private BigDecimal alternativeTargetPercent;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public BigDecimal getEquityTargetPercent() { return equityTargetPercent; }
    public void setEquityTargetPercent(BigDecimal v) { this.equityTargetPercent = v; }
    public BigDecimal getDebtTargetPercent() { return debtTargetPercent; }
    public void setDebtTargetPercent(BigDecimal v) { this.debtTargetPercent = v; }
    public BigDecimal getGoldTargetPercent() { return goldTargetPercent; }
    public void setGoldTargetPercent(BigDecimal v) { this.goldTargetPercent = v; }
    public BigDecimal getRealEstateTargetPercent() { return realEstateTargetPercent; }
    public void setRealEstateTargetPercent(BigDecimal v) { this.realEstateTargetPercent = v; }
    public BigDecimal getCashTargetPercent() { return cashTargetPercent; }
    public void setCashTargetPercent(BigDecimal v) { this.cashTargetPercent = v; }
    public BigDecimal getAlternativeTargetPercent() { return alternativeTargetPercent; }
    public void setAlternativeTargetPercent(BigDecimal v) { this.alternativeTargetPercent = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
