package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class OtherIncome extends OwnedRecord {
    private String sourceType;
    private String description;
    private BigDecimal amount;
    private String frequency;
    private LocalDate dateReceived;
    private boolean isTaxable;

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public LocalDate getDateReceived() { return dateReceived; }
    public void setDateReceived(LocalDate dateReceived) { this.dateReceived = dateReceived; }
    public boolean getIsTaxable() { return isTaxable; }
    public void setIsTaxable(boolean isTaxable) { this.isTaxable = isTaxable; }
}
