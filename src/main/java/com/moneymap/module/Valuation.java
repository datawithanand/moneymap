package com.moneymap.module;

import com.moneymap.model.User;
import com.moneymap.model.asset.*;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Record valuation math (computed fields per Sections 05–10). All values are returned in the
 * owner's base currency: USD-denominated records are converted using the owner's manually-set
 * usdInrExchangeRate for blended totals (Section 01B) — records themselves keep native values.
 */
public final class Valuation {

    private static final MathContext MC = new MathContext(12, RoundingMode.HALF_UP);

    private Valuation() {}

    public static BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** USD → base conversion using the owner's manual rate (Section 01B); INR passes through. */
    public static BigDecimal convert(BigDecimal value, String currency, User owner) {
        if (value == null) return BigDecimal.ZERO;
        if ("USD".equals(currency) && owner != null && owner.getUsdInrExchangeRate() != null
                && owner.getCurrencyPreference() == User.CurrencyPreference.INR) {
            return value.multiply(owner.getUsdInrExchangeRate(), MC);
        }
        return value;
    }

    /** FD current value: quarterly compounding on elapsed time for CUMULATIVE; principal for payout FDs. */
    public static BigDecimal fdCurrentValue(FixedDeposit fd) {
        if (fd.getPrincipalAmount() == null || fd.getStartDate() == null) return nz(fd.getPrincipalAmount());
        if (!"CUMULATIVE".equals(fd.getPayoutType())) return fd.getPrincipalAmount();
        LocalDate end = LocalDate.now().isBefore(nzDate(fd.getMaturityDate())) ? LocalDate.now() : fd.getMaturityDate();
        double years = Math.max(0, ChronoUnit.DAYS.between(fd.getStartDate(), end)) / 365.0;
        double r = nz(fd.getInterestRate()).doubleValue() / 100.0;
        double value = fd.getPrincipalAmount().doubleValue() * Math.pow(1 + r / 4, 4 * years);
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    /** FD maturity value: full-tenure quarterly compounding (CUMULATIVE), else principal. */
    public static BigDecimal fdMaturityValue(FixedDeposit fd) {
        if (fd.getPrincipalAmount() == null || fd.getStartDate() == null || fd.getMaturityDate() == null)
            return nz(fd.getPrincipalAmount());
        if (!"CUMULATIVE".equals(fd.getPayoutType())) return fd.getPrincipalAmount();
        double years = Math.max(0, ChronoUnit.DAYS.between(fd.getStartDate(), fd.getMaturityDate())) / 365.0;
        double r = nz(fd.getInterestRate()).doubleValue() / 100.0;
        return BigDecimal.valueOf(fd.getPrincipalAmount().doubleValue() * Math.pow(1 + r / 4, 4 * years))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** RD accrued value: installments paid so far, each compounded monthly to today (on-schedule assumption, Section 05 §4). */
    public static BigDecimal rdCurrentValue(RecurringDeposit rd) {
        if (rd.getMonthlyInstallmentAmount() == null || rd.getStartDate() == null || rd.getTenureMonths() == null)
            return BigDecimal.ZERO;
        long monthsElapsed = Math.min(rd.getTenureMonths(),
                Math.max(0, ChronoUnit.MONTHS.between(rd.getStartDate(), LocalDate.now()) + 1));
        double i = nz(rd.getInterestRate()).doubleValue() / 100.0 / 12.0;
        double total = 0;
        for (long k = 0; k < monthsElapsed; k++) {
            total += rd.getMonthlyInstallmentAmount().doubleValue() * Math.pow(1 + i, monthsElapsed - k);
        }
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal rdMaturityValue(RecurringDeposit rd) {
        if (rd.getMonthlyInstallmentAmount() == null || rd.getTenureMonths() == null) return BigDecimal.ZERO;
        double i = nz(rd.getInterestRate()).doubleValue() / 100.0 / 12.0;
        double total = 0;
        for (int k = 0; k < rd.getTenureMonths(); k++) {
            total += rd.getMonthlyInstallmentAmount().doubleValue() * Math.pow(1 + i, rd.getTenureMonths() - k);
        }
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal flexiRdValue(FlexiRd rd) {
        return rd.getCurrentValueOverride() != null ? rd.getCurrentValueOverride() : rd.totalDeposited();
    }

    public static BigDecimal mfCurrentValue(MutualFund mf) {
        return nz(mf.getUnitsHeld()).multiply(nz(mf.getCurrentNavPerUnit()), MC).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal mfInvested(MutualFund mf) {
        return nz(mf.getUnitsHeld()).multiply(nz(mf.getAverageNavPerUnit()), MC).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal equityValue(EquityHolding e) {
        return nz(e.getQuantity()).multiply(nz(e.getCurrentPrice()), MC).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal bondValue(Bond b) {
        BigDecimal perUnit = b.getCurrentValuePerUnit() != null ? b.getCurrentValuePerUnit() : nz(b.getFaceValue());
        return perUnit.multiply(BigDecimal.valueOf(b.getUnitsHeld() == null ? 0 : b.getUnitsHeld()), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal cryptoValue(CryptoHolding c) {
        return nz(c.getQuantity()).multiply(nz(c.getCurrentPrice()), MC).setScale(2, RoundingMode.HALF_UP);
    }

    /** ESOP: vested × max(0, FMV − strike). RSU/ESPP: vested × FMV. Unvested is informational only (Section 06 §15). */
    public static BigDecimal esopVestedValue(Esop e) {
        BigDecimal perUnit = nz(e.getCurrentFmv());
        if ("ESOP".equals(e.getGrantType())) {
            perUnit = perUnit.subtract(nz(e.getStrikePrice()));
            if (perUnit.signum() < 0) perUnit = BigDecimal.ZERO;   // underwater → ₹0, never negative
        }
        return nz(e.getVestedUnits()).multiply(perUnit, MC).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Gold value (Section 10 §19): physical gold uses weight × the owner's per-purity manual
     * rate (Section 01B); unit-based instruments (digital/SGB/ETF/MF) use units × NAV.
     */
    public static BigDecimal goldValue(GoldHolding g, User owner) {
        boolean physical = g.getGoldType() != null && g.getGoldType().startsWith("PHYSICAL");
        if (physical && g.getWeightGrams() != null && owner != null) {
            BigDecimal rate = "24K".equals(g.getPurity())
                    ? owner.getGoldRate24kPerGram() : owner.getGoldRate22kPerGram();
            if (rate != null) return g.getWeightGrams().multiply(rate, MC).setScale(2, RoundingMode.HALF_UP);
        }
        if (g.getUnits() != null && g.getCurrentNavPerUnit() != null) {
            return g.getUnits().multiply(g.getCurrentNavPerUnit(), MC).setScale(2, RoundingMode.HALF_UP);
        }
        // Fallback: purchase valuation
        BigDecimal qty = g.getWeightGrams() != null ? g.getWeightGrams() : nz(g.getUnits());
        return qty.multiply(nz(g.getPurchasePricePerGramOrUnit()), MC).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Dual SIP calculation (Section 11 §22): nominal target and inflation-adjusted target.
     * When the goal has a sipStepUpPercent set, solves for the starting SIP of a step-up
     * SIP (increased by that % every 12 months) via binary search instead of the flat-SIP
     * closed form — freefincal-style step-up planning.
     */
    public static BigDecimal[] goalSips(FinancialGoal goal) {
        if (goal.getTargetDate() == null || goal.getTargetAmountToday() == null)
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO};
        long months = Math.max(1, ChronoUnit.MONTHS.between(LocalDate.now(), goal.getTargetDate()));
        double i = nz(goal.getExpectedAnnualReturnPercent()).doubleValue() / 100.0 / 12.0;
        double target = goal.getTargetAmountToday().doubleValue();
        double inflated = target * Math.pow(1 + nz(goal.getExpectedAnnualInflationPercent()).doubleValue() / 100.0,
                months / 12.0);
        double stepUp = goal.getSipStepUpPercent() == null ? 0.0 : goal.getSipStepUpPercent().doubleValue() / 100.0;
        if (stepUp <= 0) {
            double factor = i == 0 ? months : (Math.pow(1 + i, months) - 1) / i * (1 + i);
            return new BigDecimal[]{
                    BigDecimal.valueOf(target / factor).setScale(0, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(inflated / factor).setScale(0, RoundingMode.HALF_UP)
            };
        }
        return new BigDecimal[]{
                BigDecimal.valueOf(stepUpSip(target, months, i, stepUp)).setScale(0, RoundingMode.HALF_UP),
                BigDecimal.valueOf(stepUpSip(inflated, months, i, stepUp)).setScale(0, RoundingMode.HALF_UP)
        };
    }

    /** Binary-searches the starting monthly SIP of a step-up SIP that reaches futureValue by `months`. */
    private static double stepUpSip(double futureValue, long months, double monthlyRate, double annualStepUp) {
        double lo = 0, hi = Math.max(futureValue, 1.0);
        for (int iter = 0; iter < 80; iter++) {
            double mid = (lo + hi) / 2;
            if (stepUpFutureValue(mid, months, monthlyRate, annualStepUp) < futureValue) lo = mid; else hi = mid;
        }
        return (lo + hi) / 2;
    }

    /** Accumulates a monthly SIP that grows by annualStepUp every 12 months, compounding at monthlyRate. */
    private static double stepUpFutureValue(double startingSip, long months, double monthlyRate, double annualStepUp) {
        double balance = 0, sip = startingSip;
        for (long m = 1; m <= months; m++) {
            balance = balance * (1 + monthlyRate) + sip;
            if (m % 12 == 0) sip *= (1 + annualStepUp);
        }
        return balance;
    }

    private static LocalDate nzDate(LocalDate d) { return d == null ? LocalDate.MAX : d; }
}
