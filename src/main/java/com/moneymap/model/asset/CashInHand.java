package com.moneymap.model.asset;

import java.math.BigDecimal;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class CashInHand extends OwnedRecord {
    private String label;
    private BigDecimal amount;
    private String location;
    private String currency;

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
