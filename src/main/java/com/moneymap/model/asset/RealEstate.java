package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class RealEstate extends OwnedRecord {
    private String propertyName;
    private String propertyType;
    private String address;
    private String city;
    private String state;
    private BigDecimal purchaseValue;
    private LocalDate purchaseDate;
    private BigDecimal currentEstimatedValue;
    private LocalDate lastRevaluedDate;
    private BigDecimal rentalIncomePerMonth;
    private String documentRegistrationNumber;
    private String registrarOfficeAddress;
    private String currency;

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }
    public String getPropertyType() { return propertyType; }
    public void setPropertyType(String propertyType) { this.propertyType = propertyType; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public BigDecimal getPurchaseValue() { return purchaseValue; }
    public void setPurchaseValue(BigDecimal purchaseValue) { this.purchaseValue = purchaseValue; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public BigDecimal getCurrentEstimatedValue() { return currentEstimatedValue; }
    public void setCurrentEstimatedValue(BigDecimal currentEstimatedValue) { this.currentEstimatedValue = currentEstimatedValue; }
    public LocalDate getLastRevaluedDate() { return lastRevaluedDate; }
    public void setLastRevaluedDate(LocalDate lastRevaluedDate) { this.lastRevaluedDate = lastRevaluedDate; }
    public BigDecimal getRentalIncomePerMonth() { return rentalIncomePerMonth; }
    public void setRentalIncomePerMonth(BigDecimal rentalIncomePerMonth) { this.rentalIncomePerMonth = rentalIncomePerMonth; }
    public String getDocumentRegistrationNumber() { return documentRegistrationNumber; }
    public void setDocumentRegistrationNumber(String documentRegistrationNumber) { this.documentRegistrationNumber = documentRegistrationNumber; }
    public String getRegistrarOfficeAddress() { return registrarOfficeAddress; }
    public void setRegistrarOfficeAddress(String registrarOfficeAddress) { this.registrarOfficeAddress = registrarOfficeAddress; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
