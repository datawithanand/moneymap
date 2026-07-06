package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class CryptoHolding extends OwnedRecord {
    private String coinName;
    private String coinSymbol;
    private String exchangeOrWallet;
    private BigDecimal quantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private LocalDate priceAsOfDate;
    private String currency;

    public String getCoinName() { return coinName; }
    public void setCoinName(String coinName) { this.coinName = coinName; }
    public String getCoinSymbol() { return coinSymbol; }
    public void setCoinSymbol(String coinSymbol) { this.coinSymbol = coinSymbol; }
    public String getExchangeOrWallet() { return exchangeOrWallet; }
    public void setExchangeOrWallet(String exchangeOrWallet) { this.exchangeOrWallet = exchangeOrWallet; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getAverageBuyPrice() { return averageBuyPrice; }
    public void setAverageBuyPrice(BigDecimal averageBuyPrice) { this.averageBuyPrice = averageBuyPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public LocalDate getPriceAsOfDate() { return priceAsOfDate; }
    public void setPriceAsOfDate(LocalDate priceAsOfDate) { this.priceAsOfDate = priceAsOfDate; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
