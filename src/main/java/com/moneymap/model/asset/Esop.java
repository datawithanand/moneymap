package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class Esop extends OwnedRecord {
    private String companyName;
    private String grantType;
    private LocalDate grantDate;
    private BigDecimal totalUnitsGranted;
    private BigDecimal vestedUnits;
    private BigDecimal strikePrice;
    private BigDecimal currentFmv;
    private LocalDate fmvAsOfDate;
    private String vestingScheduleNote;
    private String currency;

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getGrantType() { return grantType; }
    public void setGrantType(String grantType) { this.grantType = grantType; }
    public LocalDate getGrantDate() { return grantDate; }
    public void setGrantDate(LocalDate grantDate) { this.grantDate = grantDate; }
    public BigDecimal getTotalUnitsGranted() { return totalUnitsGranted; }
    public void setTotalUnitsGranted(BigDecimal totalUnitsGranted) { this.totalUnitsGranted = totalUnitsGranted; }
    public BigDecimal getVestedUnits() { return vestedUnits; }
    public void setVestedUnits(BigDecimal vestedUnits) { this.vestedUnits = vestedUnits; }
    public BigDecimal getStrikePrice() { return strikePrice; }
    public void setStrikePrice(BigDecimal strikePrice) { this.strikePrice = strikePrice; }
    public BigDecimal getCurrentFmv() { return currentFmv; }
    public void setCurrentFmv(BigDecimal currentFmv) { this.currentFmv = currentFmv; }
    public LocalDate getFmvAsOfDate() { return fmvAsOfDate; }
    public void setFmvAsOfDate(LocalDate fmvAsOfDate) { this.fmvAsOfDate = fmvAsOfDate; }
    public String getVestingScheduleNote() { return vestingScheduleNote; }
    public void setVestingScheduleNote(String vestingScheduleNote) { this.vestingScheduleNote = vestingScheduleNote; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
