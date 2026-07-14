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
    private BigDecimal sipStepUpPercent;         // optional annual SIP increase %, freefincal-style
    private boolean recurring;                   // e.g. an annual vacation goal rather than a one-off
    private Integer recurrenceIntervalYears;     // required when recurring = true

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
    public BigDecimal getSipStepUpPercent() { return sipStepUpPercent; }
    public void setSipStepUpPercent(BigDecimal sipStepUpPercent) { this.sipStepUpPercent = sipStepUpPercent; }
    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public Integer getRecurrenceIntervalYears() { return recurrenceIntervalYears; }
    public void setRecurrenceIntervalYears(Integer recurrenceIntervalYears) { this.recurrenceIntervalYears = recurrenceIntervalYears; }
}
