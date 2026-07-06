package com.moneymap.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Manually-triggered net worth snapshot (Section 12 §26, Section 17 §11.1). */
public class NetWorthSnapshot {
    private String id;
    private String ownerId;
    private LocalDate snapshotDate;
    private BigDecimal totalNetWorth;
    private BigDecimal totalAssets;
    private BigDecimal totalLiabilities;
    private BigDecimal cashBucket;
    private BigDecimal retirementBucket;
    private BigDecimal investmentsBucket;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public LocalDate getSnapshotDate() { return snapshotDate; }
    public void setSnapshotDate(LocalDate snapshotDate) { this.snapshotDate = snapshotDate; }
    public BigDecimal getTotalNetWorth() { return totalNetWorth; }
    public void setTotalNetWorth(BigDecimal totalNetWorth) { this.totalNetWorth = totalNetWorth; }
    public BigDecimal getTotalAssets() { return totalAssets; }
    public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }
    public BigDecimal getTotalLiabilities() { return totalLiabilities; }
    public void setTotalLiabilities(BigDecimal totalLiabilities) { this.totalLiabilities = totalLiabilities; }
    public BigDecimal getCashBucket() { return cashBucket; }
    public void setCashBucket(BigDecimal cashBucket) { this.cashBucket = cashBucket; }
    public BigDecimal getRetirementBucket() { return retirementBucket; }
    public void setRetirementBucket(BigDecimal retirementBucket) { this.retirementBucket = retirementBucket; }
    public BigDecimal getInvestmentsBucket() { return investmentsBucket; }
    public void setInvestmentsBucket(BigDecimal investmentsBucket) { this.investmentsBucket = investmentsBucket; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
