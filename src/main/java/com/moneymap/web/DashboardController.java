package com.moneymap.web;

import com.moneymap.calculator.CalculatorMath;
import com.moneymap.calculator.CalculatorValidation.ValidationException;
import com.moneymap.model.AllocationTarget;
import com.moneymap.model.NetWorthSnapshot;
import com.moneymap.model.TaxSlabSet;
import com.moneymap.model.User;
import com.moneymap.model.asset.*;
import com.moneymap.module.Buckets.AllocationClass;
import com.moneymap.module.Buckets.Bucket;
import com.moneymap.module.Valuation;
import com.moneymap.repository.Db;
import com.moneymap.repository.HouseholdMemberRepository;
import com.moneymap.repository.UserRepository;
import com.moneymap.service.PortfolioAggregationService;
import com.moneymap.service.ReminderService;
import com.moneymap.service.TaxService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.moneymap.calculator.CalculatorValidation.*;

/**
 * Dashboards (Section 12): Main Overview §25, Net Worth Trend §26, Portfolio Allocation §27,
 * Goals summary (via §25), Tax Planning §30. Every figure comes from
 * PortfolioAggregationService — no view-level math.
 */
@Controller
public class DashboardController {

    private static final Map<AllocationClass, String> COLORS = new EnumMap<>(Map.of(
            AllocationClass.EQUITY, "#4a6741",
            AllocationClass.DEBT, "#7d9bbf",
            AllocationClass.GOLD, "#b98b3e",
            AllocationClass.REAL_ESTATE, "#6e2f34",
            AllocationClass.CASH, "#8aa0a0",
            AllocationClass.ALTERNATIVE, "#b46a3c"));

    private final PortfolioAggregationService aggregation;
    private final Db db;
    private final UserRepository users;
    private final HouseholdMemberRepository householdMembers;
    private final TaxService taxService;
    private final ReminderService reminderService;

    public DashboardController(PortfolioAggregationService aggregation, Db db, UserRepository users,
                               HouseholdMemberRepository householdMembers, TaxService taxService,
                               ReminderService reminderService) {
        this.aggregation = aggregation;
        this.db = db;
        this.users = users;
        this.householdMembers = householdMembers;
        this.taxService = taxService;
        this.reminderService = reminderService;
    }

    private User user(HttpServletRequest request) { return (User) request.getAttribute("currentUser"); }

    /** The user whose data is shown — the admin-viewed user in read-only mode (§6.3), else self. */
    private User effectiveUser(HttpServletRequest request, Model model) {
        User current = user(request);
        HttpSession session = request.getSession(false);
        Object viewingId = session == null ? null : session.getAttribute(SessionKeys.ADMIN_VIEWING_USER_ID);
        if (viewingId instanceof String id) {
            User viewed = users.findById(id).orElse(null);
            if (viewed != null) {
                model.addAttribute("adminViewedUser", viewed);
                return viewed;
            }
        }
        return current;
    }

    // ── §25 Main Overview ────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false) String tag,
                            HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        var summary = aggregation.aggregate(owner, tag);
        model.addAttribute("user", owner);
        model.addAttribute("summary", summary);
        model.addAttribute("tag", tag);
        model.addAttribute("household", householdMembers.findByOwnerId(owner.getId()));
        model.addAttribute("donutGradient", donutGradient(summary));
        model.addAttribute("allocationClasses", allocationRows(summary));

        // Delta from last snapshot (§25)
        db.netWorthSnapshots.findWhere(s -> owner.getId().equals(s.getOwnerId())).stream()
                .max(Comparator.comparing(NetWorthSnapshot::getSnapshotDate))
                .ifPresent(last -> {
                    model.addAttribute("lastSnapshot", last);
                    model.addAttribute("delta", summary.netWorth.subtract(last.getTotalNetWorth()));
                });

        // 4 soonest-targetDate goals (§25)
        List<Map<String, Object>> goalRows = new ArrayList<>();
        db.financialGoals.findWhere(g -> owner.getId().equals(g.getOwnerId())).stream()
                .filter(g -> g.getTargetDate() != null)
                .sorted(Comparator.comparing(FinancialGoal::getTargetDate))
                .limit(4)
                .forEach(g -> {
                    BigDecimal[] sips = Valuation.goalSips(g);
                    goalRows.add(Map.of("goal", g, "nominalSip", sips[0], "inflationAdjustedSip", sips[1]));
                });
        model.addAttribute("goalRows", goalRows);
        model.addAttribute("reminders", reminderService.upcoming(owner, 30).stream().limit(5).toList());
        return "dashboard";
    }

    @GetMapping("/dashboard/reminders")
    public String reminders(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        model.addAttribute("reminders", reminderService.upcoming(owner, 365));
        return "dashboard/reminders";
    }

    private String donutGradient(PortfolioAggregationService.PortfolioSummary s) {
        StringBuilder sb = new StringBuilder("conic-gradient(");
        BigDecimal cursor = BigDecimal.ZERO;
        boolean any = false;
        for (AllocationClass cls : AllocationClass.values()) {
            BigDecimal pct = s.allocationPercent(cls);
            if (pct.signum() <= 0) continue;
            if (any) sb.append(", ");
            sb.append(COLORS.get(cls)).append(" ").append(cursor).append("% ")
                    .append(cursor.add(pct)).append("%");
            cursor = cursor.add(pct);
            any = true;
        }
        if (!any) return "conic-gradient(var(--surface-alt) 0% 100%)";
        return sb.append(")").toString();
    }

    private List<Map<String, Object>> allocationRows(PortfolioAggregationService.PortfolioSummary s) {
        return allocationRows(s, null);
    }

    /** rebalanceAmount = (targetPercent - actualPercent)/100 * total allocation value; positive = buy, negative = sell. */
    private List<Map<String, Object>> allocationRows(PortfolioAggregationService.PortfolioSummary s, AllocationTarget target) {
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalValue = s.allocationTotal();
        for (AllocationClass cls : AllocationClass.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", cls.name().replace('_', ' '));
            row.put("cls", cls);
            row.put("color", COLORS.get(cls));
            row.put("value", s.allocation.getOrDefault(cls, BigDecimal.ZERO));
            row.put("percent", s.allocationPercent(cls));
            BigDecimal targetPercent = targetPercentFor(target, cls);
            if (targetPercent != null) {
                BigDecimal rebalance = targetPercent.subtract(s.allocationPercent(cls))
                        .multiply(totalValue).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                row.put("rebalance", rebalance);
            }
            rows.add(row);
        }
        return rows;
    }

    private BigDecimal targetPercentFor(AllocationTarget target, AllocationClass cls) {
        if (target == null) return null;
        return switch (cls) {
            case EQUITY -> target.getEquityTargetPercent();
            case DEBT -> target.getDebtTargetPercent();
            case GOLD -> target.getGoldTargetPercent();
            case REAL_ESTATE -> target.getRealEstateTargetPercent();
            case CASH -> target.getCashTargetPercent();
            case ALTERNATIVE -> target.getAlternativeTargetPercent();
        };
    }

    // ── §26 Net Worth Trend ──────────────────────────────────────────────────

    @GetMapping("/dashboard/trend")
    public String trend(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        List<NetWorthSnapshot> snapshots = db.netWorthSnapshots
                .findWhere(s -> owner.getId().equals(s.getOwnerId())).stream()
                .sorted(Comparator.comparing(NetWorthSnapshot::getSnapshotDate))
                .toList();
        model.addAttribute("snapshots", snapshots);
        model.addAttribute("live", aggregation.aggregate(owner));
        model.addAttribute("netWorthPoints", polyline(snapshots, NetWorthSnapshot::getTotalNetWorth));
        model.addAttribute("assetsPoints", polyline(snapshots, NetWorthSnapshot::getTotalAssets));
        model.addAttribute("liabilitiesPoints", polyline(snapshots, NetWorthSnapshot::getTotalLiabilities));

        if (!snapshots.isEmpty()) {
            NetWorthSnapshot latest = snapshots.get(snapshots.size() - 1);
            NetWorthSnapshot momBase = closestOnOrBefore(snapshots, latest.getSnapshotDate().minusDays(30));
            NetWorthSnapshot yoyBase = closestOnOrBefore(snapshots, latest.getSnapshotDate().minusDays(365));
            if (momBase != null && momBase != latest) {
                model.addAttribute("momGrowthPercent", percentChange(momBase.getTotalNetWorth(), latest.getTotalNetWorth()));
            }
            if (yoyBase != null && yoyBase != latest) {
                model.addAttribute("yoyGrowthPercent", percentChange(yoyBase.getTotalNetWorth(), latest.getTotalNetWorth()));
            }
        }
        return "dashboard/trend";
    }

    private NetWorthSnapshot closestOnOrBefore(List<NetWorthSnapshot> snapshots, LocalDate cutoff) {
        return snapshots.stream().filter(s -> !s.getSnapshotDate().isAfter(cutoff))
                .max(Comparator.comparing(NetWorthSnapshot::getSnapshotDate)).orElse(null);
    }

    private BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        if (from == null || from.signum() == 0 || to == null) return null;
        return to.subtract(from).multiply(BigDecimal.valueOf(100)).divide(from.abs(), 2, RoundingMode.HALF_UP);
    }

    /** Server-rendered SVG polyline (charts draw pre-computed data only — Section 12). */
    private String polyline(List<NetWorthSnapshot> snapshots, java.util.function.Function<NetWorthSnapshot, BigDecimal> extractor) {
        if (snapshots.size() < 2) return null;
        List<BigDecimal> values = snapshots.stream().map(extractor).map(Valuation::nz).toList();
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        BigDecimal range = max.subtract(min);
        if (range.signum() == 0) range = BigDecimal.ONE;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            double x = 20 + (660.0 * i / (values.size() - 1));
            double y = 180 - values.get(i).subtract(min)
                    .multiply(BigDecimal.valueOf(160)).divide(range, 2, RoundingMode.HALF_UP).doubleValue();
            if (i > 0) sb.append(" ");
            sb.append(String.format("%.1f,%.1f", x, y));
        }
        return sb.toString();
    }

    @PostMapping("/dashboard/snapshot")
    public String takeSnapshot(HttpServletRequest request, RedirectAttributes ra) {
        User owner = user(request);
        var summary = aggregation.aggregate(owner);
        NetWorthSnapshot snap = new NetWorthSnapshot();
        snap.setOwnerId(owner.getId());
        snap.setSnapshotDate(LocalDate.now());
        snap.setTotalNetWorth(summary.netWorth);
        snap.setTotalAssets(summary.totalAssets);
        snap.setTotalLiabilities(summary.totalLiabilities);
        snap.setCashBucket(summary.bucket(com.moneymap.module.Buckets.Bucket.CASH));
        snap.setRetirementBucket(summary.bucket(com.moneymap.module.Buckets.Bucket.RETIREMENT));
        snap.setInvestmentsBucket(summary.bucket(com.moneymap.module.Buckets.Bucket.INVESTMENTS));
        // One snapshot per day: replace today's if it exists
        db.netWorthSnapshots.deleteWhere(s -> owner.getId().equals(s.getOwnerId())
                && LocalDate.now().equals(s.getSnapshotDate()));
        db.netWorthSnapshots.save(snap);
        ra.addFlashAttribute("success", "Net worth snapshot taken.");
        return "redirect:/dashboard/trend";
    }

    // ── §27 Portfolio Allocation ─────────────────────────────────────────────

    @GetMapping("/dashboard/allocation")
    public String allocation(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        var summary = aggregation.aggregate(owner);
        AllocationTarget target = db.allocationTargets
                .findWhere(t -> owner.getId().equals(t.getOwnerId())).stream().findFirst().orElse(null);
        model.addAttribute("summary", summary);
        model.addAttribute("target", target);
        model.addAttribute("donutGradient", donutGradient(summary));
        model.addAttribute("allocationClasses", allocationRows(summary, target));
        return "dashboard/allocation";
    }

    @PostMapping("/dashboard/allocation/target")
    public String saveTarget(@RequestParam BigDecimal equity, @RequestParam BigDecimal debt,
                             @RequestParam BigDecimal gold, @RequestParam BigDecimal realEstate,
                             @RequestParam BigDecimal cash, @RequestParam BigDecimal alternative,
                             HttpServletRequest request, RedirectAttributes ra) {
        User owner = user(request);
        BigDecimal sum = equity.add(debt).add(gold).add(realEstate).add(cash).add(alternative);
        if (sum.compareTo(BigDecimal.valueOf(100)) != 0) {
            ra.addFlashAttribute("error", "Target percentages must sum to exactly 100 (currently " + sum + ").");
            return "redirect:/dashboard/allocation";
        }
        AllocationTarget target = db.allocationTargets
                .findWhere(t -> owner.getId().equals(t.getOwnerId())).stream().findFirst()
                .orElseGet(AllocationTarget::new);
        target.setOwnerId(owner.getId());
        target.setEquityTargetPercent(equity);
        target.setDebtTargetPercent(debt);
        target.setGoldTargetPercent(gold);
        target.setRealEstateTargetPercent(realEstate);
        target.setCashTargetPercent(cash);
        target.setAlternativeTargetPercent(alternative);
        db.allocationTargets.save(target);
        ra.addFlashAttribute("success", "Allocation targets saved.");
        return "redirect:/dashboard/allocation";
    }

    // ── §30 Tax Planning ─────────────────────────────────────────────────────

    @GetMapping("/dashboard/tax")
    public String tax(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        String uid = owner.getId();
        BigDecimal ppf = db.ppfAccounts.findWhere(r -> uid.equals(r.getOwnerId())).stream()
                .map(PpfAccount::getYearlyContribution).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal epf = db.pfEmployerRecords.findWhere(r -> uid.equals(r.getOwnerId())
                        && "ACTIVE".equals(r.getStatus())).stream()
                .map(r -> Valuation.nz(r.getEmployeeContributionPerMonth())
                        .add(Valuation.nz(r.getVpfContributionPerMonth())).multiply(BigDecimal.valueOf(12)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal elss = db.mutualFunds.findWhere(r -> uid.equals(r.getOwnerId()) && r.getIsElss()
                        && "ACTIVE".equals(r.getSipStatus())).stream()
                .map(r -> Valuation.nz(r.getSipAmount()).multiply(BigDecimal.valueOf(12)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal lifePremiums = db.termInsurance.findWhere(r -> uid.equals(r.getOwnerId()) && r.getIs80CDeductible())
                .stream().map(r -> annualize(r.getPremiumAmount(), r.getPremiumFrequency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(db.licPolicies.findWhere(r -> uid.equals(r.getOwnerId()) && r.getIs80CDeductible())
                        .stream().map(r -> annualize(r.getPremiumAmount(), r.getPremiumFrequency()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal total80C = ppf.add(epf).add(elss).add(lifePremiums);

        BigDecimal health80D = db.healthInsurance.findWhere(r -> uid.equals(r.getOwnerId()) && r.getIs80DDeductible())
                .stream().map(r -> annualize(r.getPremiumAmount(), r.getPremiumFrequency()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal nps80CCD = db.npsAccounts.findWhere(r -> uid.equals(r.getOwnerId()) && "TIER_1".equals(r.getTier()))
                .isEmpty() ? BigDecimal.ZERO : BigDecimal.valueOf(-1);   // presence flag; contributions not tracked per year

        model.addAttribute("ppf", ppf);
        model.addAttribute("epf", epf);
        model.addAttribute("elss", elss);
        model.addAttribute("lifePremiums", lifePremiums);
        model.addAttribute("total80C", total80C);
        model.addAttribute("cap80C", BigDecimal.valueOf(150000));
        model.addAttribute("health80D", health80D);
        model.addAttribute("hasNpsTier1", nps80CCD.signum() < 0);
        List<SalaryProfile> salaryProfiles = db.salaryProfiles.findWhere(r -> uid.equals(r.getOwnerId()));
        model.addAttribute("salaryProfiles", salaryProfiles);
        model.addAttribute("taxSets", db.taxSlabSets.findAll());

        BigDecimal estimatedAnnualGrossIncome = salaryProfiles.stream()
                .map(SalaryProfile::grossMonthly).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).multiply(BigDecimal.valueOf(12));
        model.addAttribute("estimatedAnnualGrossIncome", estimatedAnnualGrossIncome);
        model.addAttribute("currentFinancialYear", db.taxSlabSets.findAll().stream()
                .map(TaxSlabSet::getFinancialYear).max(Comparator.naturalOrder()).orElse(""));
        return "dashboard/tax";
    }

    private BigDecimal annualize(BigDecimal amount, String frequency) {
        if (amount == null) return BigDecimal.ZERO;
        return switch (frequency == null ? "ANNUAL" : frequency) {
            case "MONTHLY" -> amount.multiply(BigDecimal.valueOf(12));
            case "QUARTERLY" -> amount.multiply(BigDecimal.valueOf(4));
            default -> amount;
        };
    }

    // ── Loan / Debt Overview ─────────────────────────────────────────────────

    @GetMapping("/dashboard/loans")
    public String loans(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        String uid = owner.getId();
        List<Loan> activeLoans = db.loans.findWhere(l -> uid.equals(l.getOwnerId())
                        && Valuation.nz(l.getOutstandingBalance()).signum() != 0)
                .stream().sorted(Comparator.comparing(l -> Valuation.nz(l.getOutstandingBalance()), Comparator.reverseOrder()))
                .toList();

        BigDecimal totalOutstanding = activeLoans.stream().map(Loan::getOutstandingBalance)
                .map(Valuation::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMonthlyEmi = activeLoans.stream()
                .filter(l -> !"CREDIT_CARD".equals(l.getLoanType()))
                .map(Loan::getEmiAmount).map(Valuation::nz).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyIncome = db.salaryProfiles.findWhere(r -> uid.equals(r.getOwnerId())).stream()
                .map(SalaryProfile::grossMonthly).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal emiToIncomePercent = monthlyIncome.signum() > 0
                ? totalMonthlyEmi.multiply(BigDecimal.valueOf(100)).divide(monthlyIncome, 1, RoundingMode.HALF_UP)
                : null;
        boolean highDebtBurden = emiToIncomePercent != null && emiToIncomePercent.compareTo(BigDecimal.valueOf(40)) > 0;

        List<Map<String, Object>> rows = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (Loan l : activeLoans) {
            Long monthsRemaining = null;
            if (l.getTenureMonths() != null && l.getStartDate() != null && !"CREDIT_CARD".equals(l.getLoanType())) {
                long elapsed = ChronoUnit.MONTHS.between(l.getStartDate(), today);
                monthsRemaining = Math.max(0, l.getTenureMonths() - elapsed);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("loan", l);
            row.put("monthsRemaining", monthsRemaining);
            rows.add(row);
        }

        model.addAttribute("rows", rows);
        model.addAttribute("totalOutstanding", totalOutstanding);
        model.addAttribute("totalMonthlyEmi", totalMonthlyEmi);
        model.addAttribute("monthlyIncome", monthlyIncome);
        model.addAttribute("emiToIncomePercent", emiToIncomePercent);
        model.addAttribute("highDebtBurden", highDebtBurden);
        return "dashboard/loans";
    }

    // ── Insurance Coverage ───────────────────────────────────────────────────

    @GetMapping("/dashboard/insurance")
    public String insurance(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        String uid = owner.getId();

        List<TermInsurance> termPolicies = db.termInsurance.findWhere(r -> uid.equals(r.getOwnerId()));
        List<HealthInsurance> healthPolicies = db.healthInsurance.findWhere(r -> uid.equals(r.getOwnerId()));

        BigDecimal totalTermCover = termPolicies.stream().map(TermInsurance::getSumAssured)
                .map(Valuation::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalHealthCover = healthPolicies.stream().map(HealthInsurance::getSumInsured)
                .map(Valuation::nz).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal annualIncome = db.salaryProfiles.findWhere(r -> uid.equals(r.getOwnerId())).stream()
                .map(SalaryProfile::grossMonthly).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).multiply(BigDecimal.valueOf(12));
        BigDecimal recommendedTermCover = annualIncome.multiply(BigDecimal.valueOf(12));   // common 10-15x rule of thumb, using 12x
        BigDecimal termCoverageGap = recommendedTermCover.subtract(totalTermCover).max(BigDecimal.ZERO);

        model.addAttribute("termPolicies", termPolicies);
        model.addAttribute("healthPolicies", healthPolicies);
        model.addAttribute("totalTermCover", totalTermCover);
        model.addAttribute("totalHealthCover", totalHealthCover);
        model.addAttribute("annualIncome", annualIncome);
        model.addAttribute("recommendedTermCover", recommendedTermCover);
        model.addAttribute("termCoverageGap", termCoverageGap);
        return "dashboard/insurance";
    }

    // ── Goals Dashboard ──────────────────────────────────────────────────────

    @GetMapping("/dashboard/goals")
    public String goalsDashboard(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        String uid = owner.getId();
        LocalDate today = LocalDate.now();

        List<Map<String, Object>> rows = new ArrayList<>();
        db.financialGoals.findWhere(g -> uid.equals(g.getOwnerId())).stream()
                .sorted(Comparator.comparing(FinancialGoal::getTargetDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(g -> {
                    BigDecimal[] sips = Valuation.goalSips(g);
                    Long daysRemaining = g.getTargetDate() != null ? ChronoUnit.DAYS.between(today, g.getTargetDate()) : null;
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("goal", g);
                    row.put("nominalSip", sips[0]);
                    row.put("inflationAdjustedSip", sips[1]);
                    row.put("daysRemaining", daysRemaining);
                    rows.add(row);
                });
        model.addAttribute("rows", rows);
        return "dashboard/goals";
    }

    // ── Retirement Readiness ─────────────────────────────────────────────────

    @GetMapping("/dashboard/retirement")
    public String retirementForm(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        BigDecimal retirementCorpus = aggregation.aggregate(owner).bucket(Bucket.RETIREMENT);
        model.addAttribute("currentCorpus", retirementCorpus);
        return "dashboard/retirement";
    }

    @PostMapping("/dashboard/retirement")
    public String retirementCompute(@RequestParam BigDecimal annualExpense,
                                    @RequestParam BigDecimal swrPercent,
                                    @RequestParam BigDecimal currentCorpus,
                                    @RequestParam BigDecimal monthlyInvestment,
                                    @RequestParam BigDecimal expectedReturnPercent,
                                    Model model) {
        model.addAttribute("annualExpense", annualExpense);
        model.addAttribute("swrPercent", swrPercent);
        model.addAttribute("currentCorpus", currentCorpus);
        model.addAttribute("monthlyInvestment", monthlyInvestment);
        model.addAttribute("expectedReturnPercent", expectedReturnPercent);
        try {
            check(positive(annualExpense, "Annual expense"),
                    positive(swrPercent, "Safe withdrawal rate %"),
                    nonNegative(currentCorpus, "Current retirement corpus"),
                    nonNegative(monthlyInvestment, "Monthly investment"),
                    nonNegative(expectedReturnPercent, "Expected annual return %"));
            model.addAttribute("result",
                    CalculatorMath.fire(annualExpense, swrPercent, currentCorpus, monthlyInvestment, expectedReturnPercent));
        } catch (ValidationException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "dashboard/retirement";
    }
}
