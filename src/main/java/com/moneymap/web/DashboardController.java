package com.moneymap.web;

import com.moneymap.model.AllocationTarget;
import com.moneymap.model.NetWorthSnapshot;
import com.moneymap.model.User;
import com.moneymap.model.asset.*;
import com.moneymap.module.Buckets.AllocationClass;
import com.moneymap.module.Valuation;
import com.moneymap.repository.Db;
import com.moneymap.repository.HouseholdMemberRepository;
import com.moneymap.repository.UserRepository;
import com.moneymap.service.PortfolioAggregationService;
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
import java.util.*;

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

    public DashboardController(PortfolioAggregationService aggregation, Db db, UserRepository users,
                               HouseholdMemberRepository householdMembers, TaxService taxService) {
        this.aggregation = aggregation;
        this.db = db;
        this.users = users;
        this.householdMembers = householdMembers;
        this.taxService = taxService;
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
        return "dashboard";
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
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AllocationClass cls : AllocationClass.values()) {
            rows.add(Map.of("name", cls.name().replace('_', ' '),
                    "cls", cls,
                    "color", COLORS.get(cls),
                    "value", s.allocation.getOrDefault(cls, BigDecimal.ZERO),
                    "percent", s.allocationPercent(cls)));
        }
        return rows;
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
        model.addAttribute("chartPoints", polyline(snapshots));
        return "dashboard/trend";
    }

    /** Server-rendered SVG polyline (charts draw pre-computed data only — Section 12). */
    private String polyline(List<NetWorthSnapshot> snapshots) {
        if (snapshots.size() < 2) return null;
        BigDecimal min = snapshots.stream().map(NetWorthSnapshot::getTotalNetWorth).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = snapshots.stream().map(NetWorthSnapshot::getTotalNetWorth).max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        BigDecimal range = max.subtract(min);
        if (range.signum() == 0) range = BigDecimal.ONE;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < snapshots.size(); i++) {
            double x = 20 + (660.0 * i / (snapshots.size() - 1));
            double y = 180 - snapshots.get(i).getTotalNetWorth().subtract(min)
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
        model.addAttribute("allocationClasses", allocationRows(summary));
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
        model.addAttribute("salaryProfiles", db.salaryProfiles.findWhere(r -> uid.equals(r.getOwnerId())));
        model.addAttribute("taxSets", db.taxSlabSets.findAll());
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
}
