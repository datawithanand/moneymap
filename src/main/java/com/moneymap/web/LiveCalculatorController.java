package com.moneymap.web;

import com.moneymap.calculator.CalculatorMath;
import com.moneymap.calculator.CalculatorMath.*;
import com.moneymap.model.TaxSlabSet;
import com.moneymap.model.User;
import com.moneymap.service.PortfolioAggregationService;
import com.moneymap.service.TaxService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The unified /calculators page (Calculators design handoff): a 280px calculator-list panel,
 * slider inputs that recompute live with no submit step, and a dark KPI results strip. Every
 * calculator here calls the same real, already-verified CalculatorMath formulas used by the
 * app's original per-calculator pages (kept intact at their own URLs) — this page is a new,
 * additional front end onto the same logic, not a reimplementation of the math.
 */
@Controller
public class LiveCalculatorController {

    private final PortfolioAggregationService aggregation;
    private final TaxService taxService;

    public LiveCalculatorController(PortfolioAggregationService aggregation, TaxService taxService) {
        this.aggregation = aggregation;
        this.taxService = taxService;
    }

    private User user(HttpServletRequest request) { return (User) request.getAttribute("currentUser"); }

    private static final Map<String, String> CALC_NAMES = Map.ofEntries(
            Map.entry("sip", "SIP Calculator"), Map.entry("stepup", "Step-up SIP Calculator"),
            Map.entry("swp", "SWP Calculator"), Map.entry("lumpsum", "Lumpsum Calculator"),
            Map.entry("ppf", "PPF Calculator"), Map.entry("nps", "NPS Calculator"),
            Map.entry("emi", "Loan EMI Calculator"), Map.entry("fire", "FIRE Calculator"),
            Map.entry("corpus", "Retirement Corpus Calculator"), Map.entry("goal", "Goal Planning Calculator"),
            Map.entry("tax", "Tax Regime Comparison"));

    @GetMapping("/calculators/live")
    public String page(@RequestParam(required = false, defaultValue = "sip") String type,
                        HttpServletRequest request, Model model) {
        User owner = user(request);
        BigDecimal netWorth = owner == null ? BigDecimal.ZERO : aggregation.aggregate(owner).netWorth;
        model.addAttribute("activeType", type);
        model.addAttribute("calcName", CALC_NAMES.getOrDefault(type, "Calculators"));
        model.addAttribute("defaultCorpus", netWorth.max(BigDecimal.ZERO));
        return "calculators/live";
    }

    @GetMapping("/calculators/live-compute")
    @ResponseBody
    public Map<String, Object> compute(@RequestParam String type,
                                        @RequestParam(required = false) BigDecimal amount,
                                        @RequestParam(required = false) BigDecimal years,
                                        @RequestParam(required = false) BigDecimal rate,
                                        @RequestParam(required = false) BigDecimal corpus,
                                        @RequestParam(required = false) BigDecimal withdrawal,
                                        @RequestParam(required = false) BigDecimal yearly,
                                        @RequestParam(required = false) BigDecimal monthly,
                                        @RequestParam(required = false) BigDecimal principal,
                                        @RequestParam(required = false) BigDecimal expenses,
                                        @RequestParam(required = false) BigDecimal current,
                                        @RequestParam(required = false) BigDecimal target,
                                        @RequestParam(required = false) BigDecimal stepup,
                                        @RequestParam(required = false) BigDecimal income) {
        int y = years == null ? 0 : years.intValue();
        List<Map<String, String>> results;
        String note;

        switch (type) {
            case "sip" -> {
                SipResult r = CalculatorMath.sip(amount, rate, y, BigDecimal.ZERO, false, null);
                results = List.of(
                        row("Invested amount", money(r.totalInvested())),
                        row("Estimated returns", money(r.futureValueNominal().subtract(r.totalInvested()))),
                        row("Maturity value", money(r.futureValueNominal())));
                note = "Assumes monthly SIP compounded at a fixed annual rate — actual mutual fund returns fluctuate.";
            }
            case "stepup" -> {
                SipResult r = CalculatorMath.sip(amount, rate, y, stepup, false, null);
                results = List.of(
                        row("Total invested", money(r.totalInvested())),
                        row("Estimated returns", money(r.futureValueNominal().subtract(r.totalInvested()))),
                        row("Maturity value", money(r.futureValueNominal())));
                note = "Step-up SIP increases your monthly contribution by a fixed % every year, compounding faster than a flat SIP.";
            }
            case "swp" -> {
                SwpResult r = CalculatorMath.swp(corpus, withdrawal, rate);
                results = List.of(
                        row("Monthly withdrawal", money(withdrawal)),
                        row("Corpus lasts", r.perpetual() ? "Indefinitely" : (r.monthsUntilDepletion() >= 600 ? "50+ years"
                                : String.format("%.1f years", r.monthsUntilDepletion() / 12.0))),
                        row("Starting corpus", money(corpus)));
                note = "Estimates how long a lumpsum lasts under a fixed monthly withdrawal and growth rate.";
            }
            case "lumpsum" -> {
                LumpsumResult r = CalculatorMath.lumpsum(amount, rate, y);
                results = List.of(
                        row("Invested amount", money(r.investedAmount())),
                        row("Estimated returns", money(r.estimatedReturns())),
                        row("Maturity value", money(r.maturityValue())));
                note = "One-time investment compounded annually at the assumed rate.";
            }
            case "ppf" -> {
                PpfResult r = CalculatorMath.ppf(BigDecimal.ZERO, yearly, rate, y);
                BigDecimal totalContribution = yearly.multiply(BigDecimal.valueOf(y));
                results = List.of(
                        row("Total contribution", money(totalContribution)),
                        row("Interest earned", money(r.maturityValue().subtract(totalContribution))),
                        row("Maturity value", money(r.maturityValue())));
                note = "PPF compounds annually with contributions at the start of each year; current PPF rate is 7.1% p.a.";
            }
            case "nps" -> {
                NpsResult r = CalculatorMath.nps(BigDecimal.ZERO, monthly, rate, y, BigDecimal.ZERO);
                BigDecimal totalContribution = monthly.multiply(BigDecimal.valueOf((long) y * 12));
                results = List.of(
                        row("Total contribution", money(totalContribution)),
                        row("Estimated returns", money(r.projectedCorpus().subtract(totalContribution))),
                        row("Corpus at retirement", money(r.projectedCorpus())));
                note = "NPS returns vary by asset allocation (equity/corporate bond/govt securities); this uses a blended assumption.";
            }
            case "emi" -> {
                LoanResult r = CalculatorMath.loan(principal, rate, y * 12);
                results = List.of(
                        row("Monthly EMI", money(r.emi())),
                        row("Total interest", money(r.totalInterest())),
                        row("Total payment", money(r.totalPayment())));
                note = "Standard reducing-balance EMI formula for the given principal, rate and tenure.";
            }
            case "fire" -> {
                BigDecimal annualExpense = expenses.multiply(BigDecimal.valueOf(12));
                FireResult r = CalculatorMath.fire(annualExpense, BigDecimal.valueOf(4), BigDecimal.ZERO,
                        BigDecimal.ZERO, rate);
                GoalPlanResult sipNeeded = CalculatorMath.goalPlan(r.corpusRequired(), y, rate);
                results = List.of(
                        row("FIRE corpus needed (25x)", money(r.corpusRequired())),
                        row("Years to FI", y + " yrs"),
                        row("Monthly SIP required", money(sipNeeded.requiredMonthlySip())));
                note = "Uses the 25x annual expense rule (4% safe withdrawal) to size the target corpus.";
            }
            case "corpus" -> {
                RetirementCorpusResult r = CalculatorMath.retirementCorpus(current, monthly, rate, y);
                results = List.of(
                        row("Growth of existing corpus", money(r.growthOfExisting())),
                        row("Growth of new contributions", money(r.growthOfContributions())),
                        row("Total projected corpus", money(r.totalProjectedCorpus())));
                note = "Projects existing retirement savings plus ongoing monthly contributions to your target retirement age.";
            }
            case "goal" -> {
                GoalPlanResult r = CalculatorMath.goalPlan(target, y, rate);
                results = List.of(
                        row("Goal amount", money(r.targetAmount())),
                        row("Time horizon", y + " yrs"),
                        row("Monthly SIP required", money(r.requiredMonthlySip())));
                note = "Reverse-SIP calculation: monthly investment needed to reach a target corpus by a given date.";
            }
            case "tax" -> {
                String fy = "2025-26";
                Optional<TaxSlabSet> oldSet = taxService.findSet(fy, "OLD");
                Optional<TaxSlabSet> newSet = taxService.findSet(fy, "NEW");
                if (oldSet.isEmpty() || newSet.isEmpty()) {
                    results = List.of(row("Old regime tax", "—"), row("New regime tax", "—"), row("No slabs configured", "—"));
                    note = "No tax slabs configured for FY " + fy + " — ask an administrator to add them under Admin > Tax Slabs.";
                    break;
                }
                BigDecimal oldStd = oldSet.get().getStandardDeduction() == null ? BigDecimal.ZERO : oldSet.get().getStandardDeduction();
                BigDecimal newStd = newSet.get().getStandardDeduction() == null ? BigDecimal.ZERO : newSet.get().getStandardDeduction();
                BigDecimal taxableOld = income.subtract(oldStd).subtract(BigDecimal.valueOf(200000)).max(BigDecimal.ZERO);
                BigDecimal taxableNew = income.subtract(newStd).max(BigDecimal.ZERO);
                BigDecimal taxOld = taxService.computeTax(taxableOld, oldSet.get());
                BigDecimal taxNew = taxService.computeTax(taxableNew, newSet.get());
                String betterLabel = taxNew.compareTo(taxOld) < 0 ? "New regime saves" : "Old regime saves";
                results = List.of(
                        row("Old regime tax", money(taxOld)),
                        row("New regime tax", money(taxNew)),
                        row(betterLabel, money(taxOld.subtract(taxNew).abs())));
                note = "Old regime assumes ₹2L of 80C/80D/standard deductions already applied. FY " + fy + " slabs, as configured under Admin.";
            }
            default -> throw new IllegalArgumentException("Unknown calculator type: " + type);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("results", results);
        out.put("note", note);
        return out;
    }

    private static Map<String, String> row(String label, String value) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        return m;
    }

    /** Compact ₹ Cr/L/plain formatting matching the design handoff's own fmt() helper. */
    private static String money(BigDecimal v) {
        if (v == null) return "—";
        double n = v.doubleValue();
        boolean neg = n < 0;
        n = Math.abs(n);
        String out;
        if (n >= 10000000) out = "₹" + round2(n / 10000000) + " Cr";
        else if (n >= 100000) out = "₹" + round2(n / 100000) + " L";
        else out = "₹" + String.format("%,.0f", n);
        return (neg ? "-" : "") + out;
    }

    private static String round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
