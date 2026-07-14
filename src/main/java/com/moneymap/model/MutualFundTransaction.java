package com.moneymap.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One cash-flow event against a Mutual Fund holding (Section 12: XIRR). Not an OwnedRecord —
 * it belongs to a MutualFund record, scoped indirectly to the same owner for access checks.
 */
public class MutualFundTransaction {
    public enum Type { BUY, SELL }

    private String id;
    private String ownerId;
    private String mutualFundId;
    private LocalDate date;
    private Type type;
    private BigDecimal amount;   // cash flow magnitude — always positive; sign is derived from type
    private BigDecimal units;    // optional, informational only

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getMutualFundId() { return mutualFundId; }
    public void setMutualFundId(String mutualFundId) { this.mutualFundId = mutualFundId; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }
}
