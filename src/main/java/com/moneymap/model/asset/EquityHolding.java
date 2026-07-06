package com.moneymap.model.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Schema per PRD Section 17. Common fields inherited from OwnedRecord. */
public class EquityHolding extends OwnedRecord {
    private String stockName;
    private String tickerSymbol;
    private String exchange;
    private String currency;
    private BigDecimal quantity;
    private BigDecimal averageBuyPrice;
    private BigDecimal currentPrice;
    private LocalDate priceAsOfDate;
    private String brokerName;
    private String sector;
    private boolean isEtf;
    private String allocationClassOverride;

    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public String getTickerSymbol() { return tickerSymbol; }
    public void setTickerSymbol(String tickerSymbol) { this.tickerSymbol = tickerSymbol; }
    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getAverageBuyPrice() { return averageBuyPrice; }
    public void setAverageBuyPrice(BigDecimal averageBuyPrice) { this.averageBuyPrice = averageBuyPrice; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public LocalDate getPriceAsOfDate() { return priceAsOfDate; }
    public void setPriceAsOfDate(LocalDate priceAsOfDate) { this.priceAsOfDate = priceAsOfDate; }
    public String getBrokerName() { return brokerName; }
    public void setBrokerName(String brokerName) { this.brokerName = brokerName; }
    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }
    public boolean getIsEtf() { return isEtf; }
    public void setIsEtf(boolean isEtf) { this.isEtf = isEtf; }
    public String getAllocationClassOverride() { return allocationClassOverride; }
    public void setAllocationClassOverride(String allocationClassOverride) { this.allocationClassOverride = allocationClassOverride; }
}
