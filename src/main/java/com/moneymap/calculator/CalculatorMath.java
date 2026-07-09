package com.moneymap.calculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure financial-projection math for the standalone Calculators feature. Unlike Valuation.java
 * (which values persisted asset records), these methods take plain BigDecimal/primitive inputs —
 * calculators have no backing entity.
 */
public final class CalculatorMath {

    private CalculatorMath() {}

    /** Defense-in-depth: BigDecimal.valueOf throws on NaN/Infinity, so guard with a clear message. */
    private static BigDecimal money(double v) {
        if (!Double.isFinite(v)) {
            throw new CalculatorValidation.ValidationException(
                    "That combination of inputs produces an undefined or too-large result — please adjust the values.");
        }
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    // ── FIRE ─────────────────────────────────────────────────────────────────

    public record FireResult(BigDecimal corpusRequired, BigDecimal currentCorpus,
                              BigDecimal shortfall, Integer yearsToFire) {}

    public static FireResult fire(BigDecimal annualExpense, BigDecimal swrPercent, BigDecimal currentCorpus,
                                   BigDecimal monthlyInvestment, BigDecimal expectedReturnPercent) {
        double corpusRequired = annualExpense.doubleValue() / (swrPercent.doubleValue() / 100.0);
        double balance = currentCorpus.doubleValue();
        double i = expectedReturnPercent.doubleValue() / 100.0 / 12.0;
        double monthly = monthlyInvestment.doubleValue();
        Integer years = null;
        for (int month = 1; month <= 1200; month++) {
            balance = (balance + monthly) * (1 + i);
            if (balance >= corpusRequired) { years = (month + 11) / 12; break; }
        }
        BigDecimal shortfall = money(Math.max(0, corpusRequired - currentCorpus.doubleValue()));
        return new FireResult(money(corpusRequired), currentCorpus.setScale(2, RoundingMode.HALF_UP), shortfall, years);
    }

    // ── SIP ──────────────────────────────────────────────────────────────────

    public record SipResult(BigDecimal totalInvested, BigDecimal futureValueNominal,
                             BigDecimal futureValueInflationAdjusted) {}

    public static SipResult sip(BigDecimal monthlyAmount, BigDecimal annualReturnPercent, int years,
                                 BigDecimal stepUpPercent, boolean inflationAdjust, BigDecimal inflationPercent) {
        double i = annualReturnPercent.doubleValue() / 100.0 / 12.0;
        double contribution = monthlyAmount.doubleValue();
        double stepUp = stepUpPercent == null ? 0 : stepUpPercent.doubleValue() / 100.0;
        double balance = 0;
        double invested = 0;
        int totalMonths = years * 12;
        for (int month = 1; month <= totalMonths; month++) {
            if (month > 1 && (month - 1) % 12 == 0) contribution = contribution * (1 + stepUp);
            balance = (balance + contribution) * (1 + i);
            invested += contribution;
        }
        BigDecimal nominal = money(balance);
        BigDecimal inflationAdjusted = null;
        if (inflationAdjust) {
            double infl = inflationPercent == null ? 0 : inflationPercent.doubleValue() / 100.0;
            inflationAdjusted = money(balance / Math.pow(1 + infl, years));
        }
        return new SipResult(money(invested), nominal, inflationAdjusted);
    }

    // ── SWP ──────────────────────────────────────────────────────────────────

    public record SwpResult(Integer monthsUntilDepletion, BigDecimal totalWithdrawn,
                             BigDecimal finalCorpus, boolean perpetual) {}

    public static SwpResult swp(BigDecimal corpus, BigDecimal monthlyWithdrawal, BigDecimal annualReturnPercent) {
        double i = annualReturnPercent.doubleValue() / 100.0 / 12.0;
        double balance = corpus.doubleValue();
        double withdrawal = monthlyWithdrawal.doubleValue();
        if (withdrawal <= balance * i) {
            return new SwpResult(null, null, corpus.setScale(2, RoundingMode.HALF_UP), true);
        }
        double withdrawn = 0;
        int month = 0;
        for (; month < 600 && balance > 0; month++) {
            balance = balance * (1 + i) - withdrawal;
            withdrawn += withdrawal;
        }
        return new SwpResult(month, money(withdrawn), money(Math.max(0, balance)), false);
    }

    // ── NPS ──────────────────────────────────────────────────────────────────

    public record NpsResult(BigDecimal projectedCorpus, BigDecimal lumpSumWithdrawal, BigDecimal annuityCorpus) {}

    public static NpsResult nps(BigDecimal currentCorpus, BigDecimal monthlyContribution,
                                 BigDecimal annualReturnPercent, int yearsToRetirement,
                                 BigDecimal mandatoryAnnuityPercent) {
        double i = annualReturnPercent.doubleValue() / 100.0 / 12.0;
        double balance = currentCorpus.doubleValue();
        double contribution = monthlyContribution.doubleValue();
        int months = yearsToRetirement * 12;
        for (int m = 0; m < months; m++) {
            balance = (balance + contribution) * (1 + i);
        }
        double annuityPct = mandatoryAnnuityPercent.doubleValue() / 100.0;
        BigDecimal projected = money(balance);
        BigDecimal annuity = money(balance * annuityPct);
        BigDecimal lumpSum = projected.subtract(annuity);
        return new NpsResult(projected, lumpSum, annuity);
    }

    // ── FD ───────────────────────────────────────────────────────────────────

    public record FdForwardResult(BigDecimal maturityValue, BigDecimal totalInterest) {}
    public record FdReverseResult(BigDecimal requiredPrincipal) {}

    public static FdForwardResult fdForward(BigDecimal principal, BigDecimal annualRatePercent, BigDecimal years) {
        if (principal == null) throw new CalculatorValidation.ValidationException("Principal is required.");
        double r = annualRatePercent.doubleValue() / 100.0;
        double maturity = principal.doubleValue() * Math.pow(1 + r / 4, 4 * years.doubleValue());
        BigDecimal maturityValue = money(maturity);
        BigDecimal interest = maturityValue.subtract(principal.setScale(2, RoundingMode.HALF_UP));
        return new FdForwardResult(maturityValue, interest);
    }

    public static FdReverseResult fdReverse(BigDecimal targetMaturityValue, BigDecimal annualRatePercent, BigDecimal years) {
        if (targetMaturityValue == null) throw new CalculatorValidation.ValidationException("Target maturity value is required.");
        double r = annualRatePercent.doubleValue() / 100.0;
        double principal = targetMaturityValue.doubleValue() / Math.pow(1 + r / 4, 4 * years.doubleValue());
        return new FdReverseResult(money(principal));
    }

    // ── Loan / EMI ───────────────────────────────────────────────────────────

    public record AmortizationRow(int month, BigDecimal emi, BigDecimal principalComponent,
                                   BigDecimal interestComponent, BigDecimal balance) {}
    public record LoanResult(BigDecimal emi, BigDecimal totalInterest, BigDecimal totalPayment,
                              List<AmortizationRow> schedule) {}
    public record PrepaymentResult(BigDecimal newEmi, Integer newTenureMonths, BigDecimal interestSaved,
                                    Integer tenureReducedByMonths) {}

    public static BigDecimal emiOf(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        double r = annualRatePercent.doubleValue() / 100.0 / 12.0;
        double p = principal.doubleValue();
        if (r == 0) return money(p / tenureMonths);
        double factor = Math.pow(1 + r, tenureMonths);
        return money(p * r * factor / (factor - 1));
    }

    public static LoanResult loan(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths) {
        double r = annualRatePercent.doubleValue() / 100.0 / 12.0;
        BigDecimal emi = emiOf(principal, annualRatePercent, tenureMonths);
        double balance = principal.doubleValue();
        double totalInterest = 0;
        List<AmortizationRow> schedule = new ArrayList<>();
        for (int m = 1; m <= tenureMonths; m++) {
            double interestComponent = balance * r;
            double principalComponent = Math.min(balance, emi.doubleValue() - interestComponent);
            balance = Math.max(0, balance - principalComponent);
            totalInterest += interestComponent;
            schedule.add(new AmortizationRow(m, emi, money(principalComponent), money(interestComponent), money(balance)));
        }
        BigDecimal totalInterestBd = money(totalInterest);
        BigDecimal totalPayment = emi.multiply(BigDecimal.valueOf(tenureMonths));
        return new LoanResult(emi, totalInterestBd, totalPayment, schedule);
    }

    /**
     * Simulates a lump-sum prepayment at prepaymentMonth against a fresh loan, then either holds
     * the EMI fixed (recomputing tenure) or holds tenure fixed (recomputing EMI).
     */
    public static PrepaymentResult prepay(BigDecimal principal, BigDecimal annualRatePercent, int tenureMonths,
                                           BigDecimal prepaymentAmount, int prepaymentMonth, String mode) {
        double r = annualRatePercent.doubleValue() / 100.0 / 12.0;
        BigDecimal originalEmi = emiOf(principal, annualRatePercent, tenureMonths);
        LoanResult original = loan(principal, annualRatePercent, tenureMonths);

        double balance = principal.doubleValue();
        for (int m = 1; m <= Math.min(prepaymentMonth, tenureMonths); m++) {
            double interestComponent = balance * r;
            double principalComponent = originalEmi.doubleValue() - interestComponent;
            balance = Math.max(0, balance - principalComponent);
        }
        balance = Math.max(0, balance - prepaymentAmount.doubleValue());

        int remainingOriginal = tenureMonths - prepaymentMonth;
        double interestPaid = 0;
        for (int m = 1; m <= prepaymentMonth && m <= tenureMonths; m++) {
            interestPaid += original.schedule().get(m - 1).interestComponent().doubleValue();
        }

        if (balance <= 0) {
            return new PrepaymentResult(BigDecimal.ZERO, 0, money(original.totalInterest().doubleValue() - interestPaid),
                    remainingOriginal);
        }

        if ("REDUCE_EMI".equals(mode)) {
            BigDecimal newEmi = emiOf(BigDecimal.valueOf(balance), annualRatePercent, remainingOriginal);
            double newInterest = simulateInterest(balance, r, newEmi.doubleValue(), remainingOriginal);
            double totalNewInterest = interestPaid + newInterest;
            double interestSaved = original.totalInterest().doubleValue() - totalNewInterest;
            return new PrepaymentResult(newEmi, remainingOriginal, money(Math.max(0, interestSaved)), 0);
        } else {
            int newTenure = 0;
            double b = balance;
            double emiVal = originalEmi.doubleValue();
            double newInterest = 0;
            while (b > 0 && newTenure < 1200) {
                double interestComponent = b * r;
                double principalComponent = Math.min(b, emiVal - interestComponent);
                b = Math.max(0, b - principalComponent);
                newInterest += interestComponent;
                newTenure++;
            }
            double totalNewInterest = interestPaid + newInterest;
            double interestSaved = original.totalInterest().doubleValue() - totalNewInterest;
            int tenureReducedBy = remainingOriginal - newTenure;
            return new PrepaymentResult(originalEmi, newTenure, money(Math.max(0, interestSaved)), tenureReducedBy);
        }
    }

    private static double simulateInterest(double balance, double r, double emi, int months) {
        double b = balance;
        double interest = 0;
        for (int m = 0; m < months && b > 0; m++) {
            double interestComponent = b * r;
            double principalComponent = Math.min(b, emi - interestComponent);
            b = Math.max(0, b - principalComponent);
            interest += interestComponent;
        }
        return interest;
    }

    // ── PPF ──────────────────────────────────────────────────────────────────

    public record PpfResult(BigDecimal maturityValue, List<BigDecimal> yearEndBalances) {}

    public static PpfResult ppf(BigDecimal openingBalance, BigDecimal annualContribution,
                                 BigDecimal annualRatePercent, int years) {
        double rate = annualRatePercent.doubleValue() / 100.0;
        double balance = openingBalance == null ? 0 : openingBalance.doubleValue();
        double contribution = annualContribution.doubleValue();
        List<BigDecimal> yearEnd = new ArrayList<>();
        for (int y = 0; y < years; y++) {
            balance = (balance + contribution) * (1 + rate);
            yearEnd.add(money(balance));
        }
        return new PpfResult(money(balance), yearEnd);
    }

    // ── CAGR ─────────────────────────────────────────────────────────────────

    public record CagrResult(BigDecimal cagrPercent) {}

    public static CagrResult cagr(BigDecimal startValue, BigDecimal endValue, BigDecimal years) {
        double ratio = endValue.doubleValue() / startValue.doubleValue();
        double rate = (Math.pow(ratio, 1.0 / years.doubleValue()) - 1) * 100;
        return new CagrResult(BigDecimal.valueOf(rate).setScale(2, RoundingMode.HALF_UP));
    }

    // ── Direct vs Regular Mutual Fund ───────────────────────────────────────

    public record DirectVsRegularResult(BigDecimal directValue, BigDecimal regularValue,
                                         BigDecimal difference, BigDecimal extraCostPercent) {}

    /**
     * Compounds the same investment at two CAGRs that differ only by the expense-ratio gap
     * between a fund's Direct and Regular plans — the entire "which plan costs more" question
     * reduces to compounding that one percentage-point gap over time.
     */
    public static DirectVsRegularResult directVsRegular(BigDecimal lumpSum, BigDecimal years,
                                                          BigDecimal directCagrPercent, BigDecimal regularCagrPercent) {
        double p = lumpSum.doubleValue();
        double y = years.doubleValue();
        double direct = p * Math.pow(1 + directCagrPercent.doubleValue() / 100.0, y);
        double regular = p * Math.pow(1 + regularCagrPercent.doubleValue() / 100.0, y);
        BigDecimal directValue = money(direct);
        BigDecimal regularValue = money(regular);
        BigDecimal difference = directValue.subtract(regularValue);
        BigDecimal extraCostPercent = regularValue.signum() == 0 ? BigDecimal.ZERO
                : difference.multiply(BigDecimal.valueOf(100)).divide(regularValue, 2, RoundingMode.HALF_UP);
        return new DirectVsRegularResult(directValue, regularValue, difference, extraCostPercent);
    }

    // ── Debt Mutual Fund vs Fixed Deposit ───────────────────────────────────

    public record DebtFundVsFdResult(BigDecimal fdPostTaxValue, BigDecimal debtFundPostTaxValue,
                                      BigDecimal difference, BigDecimal fdEffectivePostTaxRate,
                                      BigDecimal debtFundEffectivePostTaxRate) {}

    /**
     * Post-tax comparison under current (post-April-2023) Indian tax law: FD interest is taxed
     * annually at slab rate (approximated here as compounding at a slab-reduced rate, since FD
     * interest is taxed on accrual, not at maturity); debt mutual fund gains are taxed once at
     * redemption, also at slab rate (indexation/LTCG benefit no longer applies to debt funds
     * bought on or after 1 Apr 2023) — so the debt fund's advantage is purely the one-time
     * tax-deferral effect of paying tax at the end instead of annually, not a lower rate.
     * For funds bought before that date and eligible for grandfathered indexation, pass
     * indexationEligible=true to apply 20% LTCG tax on the inflation-indexed gain instead.
     */
    public static DebtFundVsFdResult debtFundVsFd(BigDecimal investment, BigDecimal years,
                                                    BigDecimal fdRatePercent, BigDecimal debtFundReturnPercent,
                                                    BigDecimal slabPercent, boolean indexationEligible,
                                                    BigDecimal inflationPercent) {
        double p = investment.doubleValue();
        double y = years.doubleValue();
        double slab = slabPercent.doubleValue() / 100.0;

        double fdPostTaxRate = fdRatePercent.doubleValue() / 100.0 * (1 - slab);
        double fdPostTax = p * Math.pow(1 + fdPostTaxRate, y);

        double debtFundPreTax = p * Math.pow(1 + debtFundReturnPercent.doubleValue() / 100.0, y);
        double debtFundGain = debtFundPreTax - p;
        double debtFundTax;
        if (indexationEligible) {
            double infl = inflationPercent == null ? 0 : inflationPercent.doubleValue() / 100.0;
            double indexedCost = p * Math.pow(1 + infl, y);
            double indexedGain = Math.max(0, debtFundPreTax - indexedCost);
            debtFundTax = indexedGain * 0.20;   // 20% LTCG on indexed gain (grandfathered pre-Apr-2023 rule)
        } else {
            debtFundTax = Math.max(0, debtFundGain) * slab;
        }
        double debtFundPostTax = debtFundPreTax - debtFundTax;
        double debtFundEffectiveRate = y > 0 ? Math.pow(debtFundPostTax / p, 1.0 / y) - 1 : 0;

        BigDecimal fdValue = money(fdPostTax);
        BigDecimal debtValue = money(debtFundPostTax);
        return new DebtFundVsFdResult(fdValue, debtValue, debtValue.subtract(fdValue),
                BigDecimal.valueOf(fdPostTaxRate * 100).setScale(2, RoundingMode.HALF_UP),
                BigDecimal.valueOf(debtFundEffectiveRate * 100).setScale(2, RoundingMode.HALF_UP));
    }

    // ── XIRR ─────────────────────────────────────────────────────────────────

    public record CashFlow(LocalDate date, double amount) {}

    /**
     * Money-weighted annualized return (XIRR): solves for the rate at which the NPV of dated
     * cash flows is zero, using Newton-Raphson with a bisection fallback for robustness. Sign
     * convention: outflows (investments) are negative, inflows (redemptions / current value)
     * are positive — the same convention as Excel's XIRR.
     *
     * @return the annualized rate as a percentage (e.g. 12.34 for 12.34%), or null if it can't
     *         be solved (fewer than 2 flows, all same sign, or no convergence in the search range)
     */
    public static BigDecimal xirr(List<CashFlow> flows) {
        if (flows == null || flows.size() < 2) return null;
        boolean hasPositive = flows.stream().anyMatch(f -> f.amount() > 0);
        boolean hasNegative = flows.stream().anyMatch(f -> f.amount() < 0);
        if (!hasPositive || !hasNegative) return null;

        LocalDate epoch = flows.stream().map(CashFlow::date).min(LocalDate::compareTo).orElseThrow();
        double[] years = flows.stream().mapToDouble(f -> ChronoUnit.DAYS.between(epoch, f.date()) / 365.0).toArray();
        double[] amounts = flows.stream().mapToDouble(CashFlow::amount).toArray();

        java.util.function.DoubleUnaryOperator npv = rate -> {
            double sum = 0;
            for (int i = 0; i < amounts.length; i++) sum += amounts[i] / Math.pow(1 + rate, years[i]);
            return sum;
        };
        java.util.function.DoubleUnaryOperator npvDerivative = rate -> {
            double sum = 0;
            for (int i = 0; i < amounts.length; i++) {
                if (years[i] == 0) continue;
                sum += -years[i] * amounts[i] / Math.pow(1 + rate, years[i] + 1);
            }
            return sum;
        };

        Double rate = newtonRaphson(npv, npvDerivative, 0.10);
        if (rate == null || !isValidRate(rate)) {
            rate = bisection(npv, -0.9999, 100.0);
        }
        if (rate == null || !isValidRate(rate)) return null;
        return BigDecimal.valueOf(rate * 100).setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean isValidRate(double rate) {
        return Double.isFinite(rate) && rate > -1.0 && rate < 1000;
    }

    private static Double newtonRaphson(java.util.function.DoubleUnaryOperator f,
                                         java.util.function.DoubleUnaryOperator fPrime, double guess) {
        double rate = guess;
        for (int i = 0; i < 100; i++) {
            double value = f.applyAsDouble(rate);
            if (Math.abs(value) < 1e-7) return rate;
            double derivative = fPrime.applyAsDouble(rate);
            if (derivative == 0 || !Double.isFinite(derivative)) return null;
            double next = rate - value / derivative;
            if (!Double.isFinite(next) || next <= -1.0) return null;
            rate = next;
        }
        return Math.abs(f.applyAsDouble(rate)) < 1e-4 ? rate : null;
    }

    /** Bisection fallback when Newton-Raphson fails to converge — slower but always stable if a root exists in range. */
    private static Double bisection(java.util.function.DoubleUnaryOperator f, double lo, double hi) {
        double fLo = f.applyAsDouble(lo);
        double fHi = f.applyAsDouble(hi);
        if (!Double.isFinite(fLo) || !Double.isFinite(fHi) || fLo * fHi > 0) return null;
        for (int i = 0; i < 200; i++) {
            double mid = (lo + hi) / 2;
            double fMid = f.applyAsDouble(mid);
            if (Math.abs(fMid) < 1e-7 || (hi - lo) < 1e-9) return mid;
            if (fLo * fMid < 0) { hi = mid; fHi = fMid; } else { lo = mid; fLo = fMid; }
        }
        return (lo + hi) / 2;
    }
}
