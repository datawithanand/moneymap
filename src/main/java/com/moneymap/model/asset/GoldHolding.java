package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class GoldHolding extends OwnedRecord {
    private String goldType;
    private String label;
    private String purity;
    private BigDecimal weightGrams;
    private BigDecimal units;
    private BigDecimal purchasePricePerGramOrUnit;
    private LocalDate purchaseDate;
    private BigDecimal currentNavPerUnit;
    private LocalDate navAsOfDate;
    private BigDecimal sgbCouponRate;
    private LocalDate sgbMaturityDate;
    private String currency;

    public String getGoldType() { return goldType; }
    public void setGoldType(String goldType) { this.goldType = goldType; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getPurity() { return purity; }
    public void setPurity(String purity) { this.purity = purity; }
    public BigDecimal getWeightGrams() { return weightGrams; }
    public void setWeightGrams(BigDecimal weightGrams) { this.weightGrams = weightGrams; }
    public BigDecimal getUnits() { return units; }
    public void setUnits(BigDecimal units) { this.units = units; }
    public BigDecimal getPurchasePricePerGramOrUnit() { return purchasePricePerGramOrUnit; }
    public void setPurchasePricePerGramOrUnit(BigDecimal purchasePricePerGramOrUnit) { this.purchasePricePerGramOrUnit = purchasePricePerGramOrUnit; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public BigDecimal getCurrentNavPerUnit() { return currentNavPerUnit; }
    public void setCurrentNavPerUnit(BigDecimal currentNavPerUnit) { this.currentNavPerUnit = currentNavPerUnit; }
    public LocalDate getNavAsOfDate() { return navAsOfDate; }
    public void setNavAsOfDate(LocalDate navAsOfDate) { this.navAsOfDate = navAsOfDate; }
    public BigDecimal getSgbCouponRate() { return sgbCouponRate; }
    public void setSgbCouponRate(BigDecimal sgbCouponRate) { this.sgbCouponRate = sgbCouponRate; }
    public LocalDate getSgbMaturityDate() { return sgbMaturityDate; }
    public void setSgbMaturityDate(LocalDate sgbMaturityDate) { this.sgbMaturityDate = sgbMaturityDate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
