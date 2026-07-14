package com.moneymap.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Cached mutual fund scheme reference data (Calculators/Mutual Funds fund-picker feature).
 * Shared reference data, not owned by any user — synced from api.mfapi.in on admin demand.
 * Category/fund-house/NAV are enriched lazily (per-scheme detail call), so they start null
 * for schemes only known from the bulk name/code sync.
 */
public class FundMaster {
    private String id;
    private Integer schemeCode;
    private String schemeName;
    private String fundHouse;
    private String isinGrowth;
    private String isinDivReinvestment;
    private String rawCategory;
    private String categoryBucket;   // EQUITY | DEBT | HYBRID | OTHER | null (not yet enriched)
    private BigDecimal latestNav;
    private LocalDate navAsOfDate;
    private Instant lastEnrichedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Integer getSchemeCode() { return schemeCode; }
    public void setSchemeCode(Integer schemeCode) { this.schemeCode = schemeCode; }
    public String getSchemeName() { return schemeName; }
    public void setSchemeName(String schemeName) { this.schemeName = schemeName; }
    public String getFundHouse() { return fundHouse; }
    public void setFundHouse(String fundHouse) { this.fundHouse = fundHouse; }
    public String getIsinGrowth() { return isinGrowth; }
    public void setIsinGrowth(String isinGrowth) { this.isinGrowth = isinGrowth; }
    public String getIsinDivReinvestment() { return isinDivReinvestment; }
    public void setIsinDivReinvestment(String isinDivReinvestment) { this.isinDivReinvestment = isinDivReinvestment; }
    public String getRawCategory() { return rawCategory; }
    public void setRawCategory(String rawCategory) { this.rawCategory = rawCategory; }
    public String getCategoryBucket() { return categoryBucket; }
    public void setCategoryBucket(String categoryBucket) { this.categoryBucket = categoryBucket; }
    public BigDecimal getLatestNav() { return latestNav; }
    public void setLatestNav(BigDecimal latestNav) { this.latestNav = latestNav; }
    public LocalDate getNavAsOfDate() { return navAsOfDate; }
    public void setNavAsOfDate(LocalDate navAsOfDate) { this.navAsOfDate = navAsOfDate; }
    public Instant getLastEnrichedAt() { return lastEnrichedAt; }
    public void setLastEnrichedAt(Instant lastEnrichedAt) { this.lastEnrichedAt = lastEnrichedAt; }
}
