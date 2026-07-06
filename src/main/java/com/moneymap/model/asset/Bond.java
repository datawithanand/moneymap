package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class Bond extends OwnedRecord {
    private String issuerName;
    private String bondType;
    private String isinCode;
    private BigDecimal faceValue;
    private Integer unitsHeld;
    private BigDecimal purchasePrice;
    private BigDecimal couponRate;
    private String couponFrequency;
    private LocalDate issueDate;
    private LocalDate maturityDate;
    private BigDecimal currentValuePerUnit;
    private String currency;

    public String getIssuerName() { return issuerName; }
    public void setIssuerName(String issuerName) { this.issuerName = issuerName; }
    public String getBondType() { return bondType; }
    public void setBondType(String bondType) { this.bondType = bondType; }
    public String getIsinCode() { return isinCode; }
    public void setIsinCode(String isinCode) { this.isinCode = isinCode; }
    public BigDecimal getFaceValue() { return faceValue; }
    public void setFaceValue(BigDecimal faceValue) { this.faceValue = faceValue; }
    public Integer getUnitsHeld() { return unitsHeld; }
    public void setUnitsHeld(Integer unitsHeld) { this.unitsHeld = unitsHeld; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public void setPurchasePrice(BigDecimal purchasePrice) { this.purchasePrice = purchasePrice; }
    public BigDecimal getCouponRate() { return couponRate; }
    public void setCouponRate(BigDecimal couponRate) { this.couponRate = couponRate; }
    public String getCouponFrequency() { return couponFrequency; }
    public void setCouponFrequency(String couponFrequency) { this.couponFrequency = couponFrequency; }
    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }
    public LocalDate getMaturityDate() { return maturityDate; }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate; }
    public BigDecimal getCurrentValuePerUnit() { return currentValuePerUnit; }
    public void setCurrentValuePerUnit(BigDecimal currentValuePerUnit) { this.currentValuePerUnit = currentValuePerUnit; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
