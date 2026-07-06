package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class PhysicalAsset extends OwnedRecord {
    private String assetName;
    private String assetType;
    private BigDecimal purchaseValue;
    private LocalDate purchaseDate;
    private BigDecimal currentEstimatedValue;
    private LocalDate lastRevaluedDate;
    private String registrationNumber;
    private String insurerName;
    private String insurancePolicyNumber;
    private String currency;

    public String getAssetName() { return assetName; }
    public void setAssetName(String assetName) { this.assetName = assetName; }
    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }
    public BigDecimal getPurchaseValue() { return purchaseValue; }
    public void setPurchaseValue(BigDecimal purchaseValue) { this.purchaseValue = purchaseValue; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public BigDecimal getCurrentEstimatedValue() { return currentEstimatedValue; }
    public void setCurrentEstimatedValue(BigDecimal currentEstimatedValue) { this.currentEstimatedValue = currentEstimatedValue; }
    public LocalDate getLastRevaluedDate() { return lastRevaluedDate; }
    public void setLastRevaluedDate(LocalDate lastRevaluedDate) { this.lastRevaluedDate = lastRevaluedDate; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getInsurerName() { return insurerName; }
    public void setInsurerName(String insurerName) { this.insurerName = insurerName; }
    public String getInsurancePolicyNumber() { return insurancePolicyNumber; }
    public void setInsurancePolicyNumber(String insurancePolicyNumber) { this.insurancePolicyNumber = insurancePolicyNumber; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
