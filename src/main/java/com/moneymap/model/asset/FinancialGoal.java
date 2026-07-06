package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class FinancialGoal extends OwnedRecord {
    private String goalName;
    private String goalType;
    private BigDecimal targetAmountToday;
    private LocalDate targetDate;
    private BigDecimal expectedAnnualReturnPercent;
    private BigDecimal expectedAnnualInflationPercent;

    public String getGoalName() { return goalName; }
    public void setGoalName(String goalName) { this.goalName = goalName; }
    public String getGoalType() { return goalType; }
    public void setGoalType(String goalType) { this.goalType = goalType; }
    public BigDecimal getTargetAmountToday() { return targetAmountToday; }
    public void setTargetAmountToday(BigDecimal targetAmountToday) { this.targetAmountToday = targetAmountToday; }
    public LocalDate getTargetDate() { return targetDate; }
    public void setTargetDate(LocalDate targetDate) { this.targetDate = targetDate; }
    public BigDecimal getExpectedAnnualReturnPercent() { return expectedAnnualReturnPercent; }
    public void setExpectedAnnualReturnPercent(BigDecimal expectedAnnualReturnPercent) { this.expectedAnnualReturnPercent = expectedAnnualReturnPercent; }
    public BigDecimal getExpectedAnnualInflationPercent() { return expectedAnnualInflationPercent; }
    public void setExpectedAnnualInflationPercent(BigDecimal expectedAnnualInflationPercent) { this.expectedAnnualInflationPercent = expectedAnnualInflationPercent; }
}
