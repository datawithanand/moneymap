package com.moneymap.model.expense;

import com.moneymap.model.asset.OwnedRecord;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A recurring expense/income template. The RecurringTransactionScheduler generates a new
 * ExpenseEntry each time nextDueDate arrives and advances nextDueDate by frequency.
 */
public class RecurringRule extends OwnedRecord {

    private String category;
    private BigDecimal amount;
    private String direction = "EXPENSE";
    /** DAILY, WEEKLY, MONTHLY, YEARLY */
    private String frequency;
    private LocalDate nextDueDate;
    private boolean active = true;
    private String note;
    private String currency = "INR";

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getFrequency() { return frequency; }
    public void setFrequency(String frequency) { this.frequency = frequency; }
    public LocalDate getNextDueDate() { return nextDueDate; }
    public void setNextDueDate(LocalDate nextDueDate) { this.nextDueDate = nextDueDate; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public LocalDate advance(LocalDate from) {
        return switch (frequency == null ? "MONTHLY" : frequency) {
            case "DAILY" -> from.plusDays(1);
            case "WEEKLY" -> from.plusWeeks(1);
            case "YEARLY" -> from.plusYears(1);
            default -> from.plusMonths(1);
        };
    }
}
