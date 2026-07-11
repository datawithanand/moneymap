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

    /**
     * Fixed categorical palette (validated for colorblind-safe adjacent contrast and surface
     * contrast in both light and dark mode — see dataviz skill). Deliberately independent of the
     * active color theme: chart series need a distinct identity palette, not the site's single
     * accent color repeated in muted variants (which is why every chart used to look monochrome).
     * Order is fixed and never cycled per-render — same slot always means the same category.
     */
    private static final String[] CHART_PALETTE_LIGHT =
            {"#2a78d6", "#1baf7a", "#eda100", "#008300", "#4a3aa7", "#e34948", "#e87ba4", "#eb6834"};
    private static final String[] CHART_PALETTE_DARK =
            {"#3987e5", "#199e70", "#c98500", "#008300", "#9085e9", "#e66767", "#d55181", "#d95926"};

    private static final Map<AllocationClass, String> COLORS = new EnumMap<>(Map.of(
            AllocationClass.EQUITY, CHART_PALETTE_LIGHT[0],
            AllocationClass.DEBT, CHART_PALETTE_LIGHT[1],
            AllocationClass.GOLD, CHART_PALETTE_LIGHT[2],
            AllocationClass.REAL_ESTATE, CHART_PALETTE_LIGHT[3],
            AllocationClass.CASH, CHART_PALETTE_LIGHT[4],
            AllocationClass.ALTERNATIVE, CHART_PALETTE_LIGHT[5]));
    private static final Map<AllocationClass, String> COLORS_DARK = new EnumMap<>(Map.of(
            AllocationClass.EQUITY, CHART_PALETTE_DARK[0],
            AllocationClass.DEBT, CHART_PALETTE_DARK[1],
            AllocationClass.GOLD, CHART_PALETTE_DARK[2],
            AllocationClass.REAL_ESTATE, CHART_PALETTE_DARK[3],
            AllocationClass.CASH, CHART_PALETTE_DARK[4],
            AllocationClass.ALTERNATIVE, CHART_PALETTE_DARK[5]));

    private final PortfolioAggregationService aggregation;
    private final Db db;
    private final UserRepository users;
    private final HouseholdMemberRepository householdMembers;
    private final TaxService taxService;
    private final ReminderService reminderService;
    private final com.moneymap.repository.GlobalSettingsRepository globalSettings;
    private final com.moneymap.module.ModuleRegistry registry;
    private final DateFmt fmt;

    public DashboardController(PortfolioAggregationService aggregation, Db db, UserRepository users,
                               HouseholdMemberRepository householdMembers, TaxService taxService,
                               ReminderService reminderService,
                               com.moneymap.repository.GlobalSettingsRepository globalSettings,
                               com.moneymap.module.ModuleRegistry registry, DateFmt fmt) {
        this.aggregation = aggregation;
        this.db = db;
        this.users = users;
        this.householdMembers = householdMembers;
        this.taxService = taxService;
        this.reminderService = reminderService;
        this.globalSettings = globalSettings;
        this.registry = registry;
        this.fmt = fmt;
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
        model.addAttribute("allocationClasses", allocationRows(summary));
        model.addAttribute("cashBreakdown", bucketBreakdown(summary, Bucket.CASH, owner));
        model.addAttribute("retirementBreakdown", bucketBreakdown(summary, Bucket.RETIREMENT, owner));
        model.addAttribute("investmentsBreakdown", bucketBreakdown(summary, Bucket.INVESTMENTS, owner));

        // Delta from last snapshot (§25)
        List<NetWorthSnapshot> ownerSnapshots = db.netWorthSnapshots
                .findWhere(s -> owner.getId().equals(s.getOwnerId())).stream()
                .sorted(Comparator.comparing(NetWorthSnapshot::getSnapshotDate))
                .toList();
        if (!ownerSnapshots.isEmpty()) {
            NetWorthSnapshot last = ownerSnapshots.get(ownerSnapshots.size() - 1);
            model.addAttribute("lastSnapshot", last);
            model.addAttribute("delta", summary.netWorth.subtract(last.getTotalNetWorth()));
        }
        // Compact trend series for the dashboard's mini chart — last 12 snapshots, oldest first.
        List<NetWorthSnapshot> recentSnapshots = ownerSnapshots.size() > 12
                ? ownerSnapshots.subList(ownerSnapshots.size() - 12, ownerSnapshots.size())
                : ownerSnapshots;
        model.addAttribute("trendLabels", recentSnapshots.stream()
                .map(s -> s.getSnapshotDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM")))
                .toList());
        model.addAttribute("trendValues", recentSnapshots.stream()
                .map(NetWorthSnapshot::getTotalNetWorth).toList());

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
        model.addAttribute("recentActivity", recentActivity(owner, 8));
        return "dashboard";
    }

    @GetMapping("/dashboard/reminders")
    public String reminders(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        model.addAttribute("reminders", reminderService.upcoming(owner, 365));
        return "dashboard/reminders";
    }

    /**
     * The most recently added/edited records across every module, newest first — MoneyMap has
     * no separate expense/income transaction ledger (it's a net-worth tracker, not a budgeting
     * app), so this surfaces record activity as the closest honest equivalent to "recent
     * transactions" rather than fabricating data that doesn't exist.
     */
    private List<Map<String, Object>> recentActivity(User owner, int limit) {
        record Activity(String moduleName, String modulePath, String label, java.time.Instant when, boolean isNew) {}
        List<Activity> all = new ArrayList<>();
        for (var def : registry.all()) {
            for (Object record : def.repo.findWhere(r -> owner.getId().equals(((com.moneymap.model.asset.OwnedRecord) r).getOwnerId()))) {
                var owned = (com.moneymap.model.asset.OwnedRecord) record;
                java.time.Instant when = owned.getUpdatedAt() != null ? owned.getUpdatedAt() : owned.getCreatedAt();
                if (when == null) continue;
                boolean isNew = owned.getCreatedAt() != null && owned.getCreatedAt().equals(when);
                String label = recordLabel(def, owned);
                all.add(new Activity(def.displayName, def.path, label, when, isNew));
            }
        }
        return all.stream()
                .sorted(Comparator.comparing(Activity::when).reversed())
                .limit(limit)
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("moduleName", a.moduleName());
                    m.put("modulePath", a.modulePath());
                    m.put("label", a.label());
                    m.put("when", a.when());
                    m.put("isNew", a.isNew());
                    return m;
                }).toList();
    }

    /** Best-effort human label for a record: the first configured text-ish field, else the module name. */
    private String recordLabel(com.moneymap.module.ModuleDef<?> def, com.moneymap.model.asset.OwnedRecord record) {
        if (def.fields.isEmpty()) return def.displayName;
        org.springframework.beans.BeanWrapper bw = new org.springframework.beans.BeanWrapperImpl(record);
        for (var f : def.fields) {
            if (!"text".equals(f.type()) && !"select".equals(f.type())) continue;
            Object v = bw.isReadableProperty(f.name()) ? bw.getPropertyValue(f.name()) : null;
            if (v != null && !v.toString().isBlank()) return v.toString();
        }
        return def.displayName;
    }

    /**
     * Which modules make up one bucket's total, so "Cash", "Retirement", and "Investments" show
     * what's actually driving that number instead of a bare figure with no composition — e.g. a
     * Retirement total that's 100% PPF and 0% EPF/NPS is a real, useful signal, not decoration.
     */
    private List<Map<String, Object>> bucketBreakdown(PortfolioAggregationService.PortfolioSummary s, Bucket bucket, User owner) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (var ms : s.moduleSummaries) {
            if (ms.bucket() != bucket || ms.total().signum() <= 0) continue;
            entries.add(Map.of("name", (Object) ms.displayName(), "total", ms.total()));
        }
        return namedBreakdown(entries, owner);
    }

    /**
     * Builds a composition-donut-ready list from (name, total) pairs: sorted by total descending,
     * with a fixed-order categorical color assigned per row and money pre-formatted for tooltips.
     * Shared by every "what's this total made of" chart (bucket cards, 80C, loans, insurance).
     */
    private List<Map<String, Object>> namedBreakdown(List<Map<String, Object>> entries, User owner) {
        List<Map<String, Object>> rows = new ArrayList<>(entries);
        rows.sort((a, b) -> ((BigDecimal) b.get("total")).compareTo((BigDecimal) a.get("total")));
        List<Map<String, Object>> out = new ArrayList<>();
        int colorIndex = 0;
        for (var entry : rows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", entry.get("name"));
            row.put("total", entry.get("total"));
            row.put("formattedTotal", fmt.money((BigDecimal) entry.get("total"), owner));
            row.put("color", CHART_PALETTE_LIGHT[colorIndex % CHART_PALETTE_LIGHT.length]);
            row.put("colorDark", CHART_PALETTE_DARK[colorIndex % CHART_PALETTE_DARK.length]);
            out.add(row);
            colorIndex++;
        }
        return out;
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

    private static final double DONUT_RADIUS = 80.0;
    private static final double DONUT_CIRCUMFERENCE = 2 * Math.PI * DONUT_RADIUS;

    /** rebalanceAmount = (targetPercent - actualPercent)/100 * total allocation value; positive = buy, negative = sell. */
    private List<Map<String, Object>> allocationRows(PortfolioAggregationService.PortfolioSummary s, AllocationTarget target) {
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalValue = s.allocationTotal();
        double cumulativePercent = 0;
        for (AllocationClass cls : AllocationClass.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", cls.name().replace('_', ' '));
            row.put("cls", cls);
            row.put("color", COLORS.get(cls));
            row.put("colorDark", COLORS_DARK.get(cls));
            row.put("value", s.allocation.getOrDefault(cls, BigDecimal.ZERO));
            BigDecimal percent = s.allocationPercent(cls);
            row.put("percent", percent);
            // Precomputed SVG donut arc geometry — server-side only, template just draws it (Section 12).
            double segmentLength = percent.doubleValue() / 100.0 * DONUT_CIRCUMFERENCE;
            row.put("dashArray", String.format("%.2f %.2f", segmentLength, DONUT_CIRCUMFERENCE - segmentLength));
            row.put("rotateDeg", String.format("%.2f", cumulativePercent * 3.6 - 90));
            cumulativePercent += percent.doubleValue();
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
        // Chart.js line chart data — all three series share one axis (same currency unit), unlike
        // the old hand-rolled SVG chart which scaled each line independently and so couldn't show
        // how Net Worth actually relates to Assets and Liabilities.
        model.addAttribute("trendChartLabels", snapshots.stream()
                .map(s -> s.getSnapshotDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")))
                .toList());
        model.addAttribute("trendChartNetWorth", snapshots.stream().map(NetWorthSnapshot::getTotalNetWorth).toList());
        model.addAttribute("trendChartAssets", snapshots.stream().map(NetWorthSnapshot::getTotalAssets).toList());
        model.addAttribute("trendChartLiabilities", snapshots.stream().map(NetWorthSnapshot::getTotalLiabilities).toList());
        model.addAttribute("trendChartNetWorthFmt", snapshots.stream().map(s -> fmt.money(s.getTotalNetWorth(), owner)).toList());
        model.addAttribute("trendChartAssetsFmt", snapshots.stream().map(s -> fmt.money(s.getTotalAssets(), owner)).toList());
        model.addAttribute("trendChartLiabilitiesFmt", snapshots.stream().map(s -> fmt.money(s.getTotalLiabilities(), owner)).toList());

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
        List<Map<String, Object>> performers = performers(owner);
        model.addAttribute("bestPerformers", performers.stream().limit(3).toList());
        model.addAttribute("worstPerformers", performers.reversed().stream().limit(3).toList());
        return "dashboard/trend";
    }

    /** Mutual funds + stocks ranked by gain %, best first. Only instruments with a positive invested amount are ranked. */
    private List<Map<String, Object>> performers(User owner) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MutualFund mf : db.mutualFunds.findWhere(f -> owner.getId().equals(f.getOwnerId()))) {
            BigDecimal invested = Valuation.mfInvested(mf);
            if (invested.signum() <= 0) continue;
            BigDecimal current = Valuation.mfCurrentValue(mf);
            BigDecimal gainPercent = current.subtract(invested).multiply(BigDecimal.valueOf(100))
                    .divide(invested, 2, RoundingMode.HALF_UP);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", mf.getFundName());
            row.put("moduleName", "Mutual Fund");
            row.put("modulePath", "mutual-funds");
            row.put("recordId", mf.getId());
            row.put("gainPercent", gainPercent);
            rows.add(row);
        }
        for (EquityHolding e : db.equityHoldings.findWhere(h -> owner.getId().equals(h.getOwnerId()))) {
            BigDecimal invested = Valuation.nz(e.getQuantity()).multiply(Valuation.nz(e.getAverageBuyPrice()));
            if (invested.signum() <= 0) continue;
            BigDecimal current = Valuation.equityValue(e);
            BigDecimal gainPercent = current.subtract(invested).multiply(BigDecimal.valueOf(100))
                    .divide(invested, 2, RoundingMode.HALF_UP);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", e.getStockName());
            row.put("moduleName", "Stock / ETF");
            row.put("modulePath", "stocks");
            row.put("recordId", e.getId());
            row.put("gainPercent", gainPercent);
            rows.add(row);
        }
        rows.sort((a, b) -> ((BigDecimal) b.get("gainPercent")).compareTo((BigDecimal) a.get("gainPercent")));
        return rows;
    }

    private NetWorthSnapshot closestOnOrBefore(List<NetWorthSnapshot> snapshots, LocalDate cutoff) {
        return snapshots.stream().filter(s -> !s.getSnapshotDate().isAfter(cutoff))
                .max(Comparator.comparing(NetWorthSnapshot::getSnapshotDate)).orElse(null);
    }

    private BigDecimal percentChange(BigDecimal from, BigDecimal to) {
        if (from == null || from.signum() == 0 || to == null) return null;
        return to.subtract(from).multiply(BigDecimal.valueOf(100)).divide(from.abs(), 2, RoundingMode.HALF_UP);
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
        model.addAttribute("allocationTotal", summary.allocationTotal());
        if (target != null && target.getAge() != null) {
            model.addAttribute("glidePath", glidePathSuggestion(target.getAge()));
        }
        return "dashboard/allocation";
    }

    /**
     * Age-based equity glide path (Section 12 §27): equity% = 110 - age, clamped to a sane
     * 20-90 band (the common "110 minus age" variant of the rule of thumb, slightly more
     * equity-friendly than "100 minus age" to account for longer life expectancy). The
     * remainder is split 70% debt / 20% gold / 10% cash as a reasonable stable default —
     * the user can freely override any of these in the target form below.
     */
    private Map<String, BigDecimal> glidePathSuggestion(int age) {
        int equityPercent = Math.max(20, Math.min(90, 110 - age));
        BigDecimal equity = BigDecimal.valueOf(equityPercent);
        BigDecimal remainder = BigDecimal.valueOf(100 - equityPercent);
        BigDecimal debt = remainder.multiply(BigDecimal.valueOf(70)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal gold = remainder.multiply(BigDecimal.valueOf(20)).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
        BigDecimal cash = remainder.subtract(debt).subtract(gold);   // absorbs rounding so the total is exactly 100
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        out.put("equity", equity);
        out.put("debt", debt);
        out.put("gold", gold);
        out.put("cash", cash);
        return out;
    }

    @PostMapping("/dashboard/allocation/age")
    public String saveAllocationAge(@RequestParam Integer age, HttpServletRequest request, RedirectAttributes ra) {
        User owner = user(request);
        if (age < 0 || age > 120) {
            ra.addFlashAttribute("error", "Enter a realistic age (0-120).");
            return "redirect:/dashboard/allocation";
        }
        AllocationTarget target = db.allocationTargets
                .findWhere(t -> owner.getId().equals(t.getOwnerId())).stream().findFirst()
                .orElseGet(AllocationTarget::new);
        target.setOwnerId(owner.getId());
        target.setAge(age);
        if (target.getEquityTargetPercent() == null) {
            // First-time setup: seed all six targets from the suggestion so the record is complete and sums to 100.
            Map<String, BigDecimal> g = glidePathSuggestion(age);
            target.setEquityTargetPercent(g.get("equity"));
            target.setDebtTargetPercent(g.get("debt"));
            target.setGoldTargetPercent(g.get("gold"));
            target.setCashTargetPercent(g.get("cash"));
            target.setRealEstateTargetPercent(BigDecimal.ZERO);
            target.setAlternativeTargetPercent(BigDecimal.ZERO);
        }
        db.allocationTargets.save(target);
        return "redirect:/dashboard/allocation";
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
        BigDecimal cap80C = BigDecimal.valueOf(150000);
        model.addAttribute("total80C", total80C);
        model.addAttribute("cap80C", cap80C);
        model.addAttribute("cap80CPercent", total80C.min(cap80C)
                .multiply(BigDecimal.valueOf(100)).divide(cap80C, 0, RoundingMode.HALF_UP));
        model.addAttribute("health80D", health80D);
        model.addAttribute("hasNpsTier1", nps80CCD.signum() < 0);
        List<Map<String, Object>> entries80C = new ArrayList<>();
        if (ppf.signum() > 0) entries80C.add(Map.of("name", (Object) "PPF", "total", ppf));
        if (epf.signum() > 0) entries80C.add(Map.of("name", (Object) "EPF/VPF", "total", epf));
        if (elss.signum() > 0) entries80C.add(Map.of("name", (Object) "ELSS SIPs", "total", elss));
        if (lifePremiums.signum() > 0) entries80C.add(Map.of("name", (Object) "Life insurance premiums", "total", lifePremiums));
        model.addAttribute("breakdown80C", namedBreakdown(entries80C, owner));
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
        BigDecimal emiBarPercent = emiToIncomePercent != null ? emiToIncomePercent.min(BigDecimal.valueOf(100)) : null;

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
        model.addAttribute("outstandingBreakdown", namedBreakdown(activeLoans.stream()
                .map(l -> Map.of("name", (Object) l.getLenderName(), "total", Valuation.nz(l.getOutstandingBalance())))
                .toList(), owner));
        model.addAttribute("highDebtBurden", highDebtBurden);
        model.addAttribute("emiBarPercent", emiBarPercent);
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
        // Coverage bar width, precomputed (Section 12: template only draws, never computes).
        BigDecimal coveragePercent = recommendedTermCover.signum() > 0
                ? totalTermCover.multiply(BigDecimal.valueOf(100)).divide(recommendedTermCover, 0, RoundingMode.HALF_UP).min(BigDecimal.valueOf(100))
                : null;

        model.addAttribute("termPolicies", termPolicies);
        model.addAttribute("healthPolicies", healthPolicies);
        model.addAttribute("totalTermCover", totalTermCover);
        model.addAttribute("totalHealthCover", totalHealthCover);
        model.addAttribute("annualIncome", annualIncome);
        model.addAttribute("recommendedTermCover", recommendedTermCover);
        model.addAttribute("termCoverageGap", termCoverageGap);
        model.addAttribute("coveragePercent", coveragePercent);
        model.addAttribute("termCoverBreakdown", namedBreakdown(termPolicies.stream()
                .map(p -> Map.of("name", (Object) p.getInsurerName(), "total", Valuation.nz(p.getSumAssured())))
                .toList(), owner));
        model.addAttribute("healthCoverBreakdown", namedBreakdown(healthPolicies.stream()
                .map(p -> Map.of("name", (Object) p.getInsurerName(), "total", Valuation.nz(p.getSumInsured())))
                .toList(), owner));
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
            CalculatorMath.FireResult result =
                    CalculatorMath.fire(annualExpense, swrPercent, currentCorpus, monthlyInvestment, expectedReturnPercent);
            model.addAttribute("result", result);
            if (result.corpusRequired().signum() > 0) {
                model.addAttribute("readinessPercent", result.currentCorpus()
                        .multiply(BigDecimal.valueOf(100)).divide(result.corpusRequired(), 0, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100)));
            }
        } catch (ValidationException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "dashboard/retirement";
    }

    // ── Nifty Valuation Tool ─────────────────────────────────────────────────

    @GetMapping("/dashboard/nifty-valuation")
    public String niftyValuation(Model model) {
        var settings = globalSettings.get();
        BigDecimal pe = settings.getNiftyPe();
        BigDecimal avgPe = settings.getNiftyHistoricalAvgPe();
        model.addAttribute("niftyPe", pe);
        model.addAttribute("niftyHistoricalAvgPe", avgPe);
        model.addAttribute("niftyPeUpdatedAt", settings.getNiftyPeUpdatedAt());
        if (pe != null && avgPe != null && avgPe.signum() > 0) {
            BigDecimal ratioPercent = pe.multiply(BigDecimal.valueOf(100))
                    .divide(avgPe, 1, RoundingMode.HALF_UP);   // pe/avgPe as a %, e.g. 113.4 = 13.4% above average
            model.addAttribute("niftyRatioPercent", ratioPercent);
            model.addAttribute("niftyZone", valuationZone(ratioPercent));
        }
        return "dashboard/nifty-valuation";
    }

    /**
     * Standard PE-band valuation heuristic (freefincal-style): compares current PE to the
     * long-run average PE. Purely informational — not investment advice.
     */
    private String valuationZone(BigDecimal ratioPercent) {
        double r = ratioPercent.doubleValue();
        if (r < 85) return "UNDERVALUED";
        if (r <= 110) return "FAIRLY_VALUED";
        if (r <= 130) return "OVERVALUED";
        return "HIGHLY_OVERVALUED";
    }

    // ── Retirement Bucket Strategy Planner ───────────────────────────────────

    @GetMapping("/dashboard/retirement-buckets")
    public String retirementBuckets(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        BigDecimal corpus = aggregation.aggregate(owner).bucket(Bucket.RETIREMENT);
        model.addAttribute("corpus", corpus);
        return "dashboard/retirement-buckets";
    }

    @PostMapping("/dashboard/retirement-buckets")
    public String retirementBucketsCompute(@RequestParam BigDecimal corpus,
                                           @RequestParam BigDecimal annualExpense,
                                           Model model) {
        model.addAttribute("corpus", corpus);
        model.addAttribute("annualExpenseInput", annualExpense);
        try {
            check(nonNegative(corpus, "Retirement corpus"), positive(annualExpense, "Annual expense"));
            // Freefincal-style 3-bucket drawdown: bucket 1 (years 1-3) in cash/liquid funds,
            // bucket 2 (years 4-7) in short-duration debt, bucket 3 (year 8+) stays invested in
            // equity/growth assets and is periodically used to refill buckets 1 and 2.
            BigDecimal bucket1 = annualExpense.multiply(BigDecimal.valueOf(3)).min(corpus);
            BigDecimal bucket2 = annualExpense.multiply(BigDecimal.valueOf(4)).min(corpus.subtract(bucket1));
            BigDecimal bucket3 = corpus.subtract(bucket1).subtract(bucket2).max(BigDecimal.ZERO);
            boolean shortfall = bucket1.add(bucket2).compareTo(annualExpense.multiply(BigDecimal.valueOf(7))) < 0;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bucket1", bucket1);
            result.put("bucket2", bucket2);
            result.put("bucket3", bucket3);
            result.put("shortfall", shortfall);
            model.addAttribute("result", result);
        } catch (ValidationException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "dashboard/retirement-buckets";
    }

    // ── Financial Health Score ───────────────────────────────────────────────

    @GetMapping("/dashboard/health-score")
    public String healthScore(HttpServletRequest request, Model model) {
        User owner = effectiveUser(request, model);
        String uid = owner.getId();
        var summary = aggregation.aggregate(owner);

        com.moneymap.model.FinancialHealthInputs inputs = db.financialHealthInputs
                .findWhere(x -> uid.equals(x.getOwnerId())).stream().findFirst().orElse(null);
        model.addAttribute("inputs", inputs);
        if (inputs == null || inputs.getMonthlyExpense() == null || inputs.getMonthlyExpense().signum() <= 0) {
            return "dashboard/health-score";
        }

        BigDecimal monthlyExpense = inputs.getMonthlyExpense();
        BigDecimal cashBucket = summary.bucket(Bucket.CASH);
        BigDecimal emergencyMonths = cashBucket.divide(monthlyExpense, 1, RoundingMode.HALF_UP);
        int emergencyScore = scoreBand(emergencyMonths.doubleValue(), 3, 6);   // 3mo=partial, 6mo=full credit

        BigDecimal totalTermCover = db.termInsurance.findWhere(r -> uid.equals(r.getOwnerId()))
                .stream().map(TermInsurance::getSumAssured).map(Valuation::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal annualIncome = db.salaryProfiles.findWhere(r -> uid.equals(r.getOwnerId())).stream()
                .map(SalaryProfile::grossMonthly).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).multiply(BigDecimal.valueOf(12));
        Integer insuranceScore = null;
        if (annualIncome.signum() > 0) {
            BigDecimal recommended = annualIncome.multiply(BigDecimal.valueOf(12));
            double coverRatio = totalTermCover.multiply(BigDecimal.valueOf(100))
                    .divide(recommended, 1, RoundingMode.HALF_UP).doubleValue();
            insuranceScore = scoreBand(coverRatio, 50, 100);
        }

        BigDecimal totalMonthlyEmi = activeLoansMonthlyEmi(uid);
        BigDecimal monthlyIncome = annualIncome.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        Integer debtScore = null;
        if (monthlyIncome.signum() > 0) {
            double emiPercent = totalMonthlyEmi.multiply(BigDecimal.valueOf(100))
                    .divide(monthlyIncome, 1, RoundingMode.HALF_UP).doubleValue();
            // Inverted band: EMI/income at or below 20% scores 100, at or above 40% scores 0.
            debtScore = Math.max(0, Math.min(100, 100 - (int) Math.round(Math.max(0, emiPercent - 20) * (100.0 / 20))));
        }

        BigDecimal savingsRateInput = inputs.getMonthlySavings();
        Integer savingsScore = null;
        if (savingsRateInput != null && monthlyIncome.signum() > 0) {
            double savingsPercent = savingsRateInput.multiply(BigDecimal.valueOf(100))
                    .divide(monthlyIncome, 1, RoundingMode.HALF_UP).doubleValue();
            savingsScore = scoreBand(savingsPercent, 10, 20);
        }

        List<Integer> componentScores = new ArrayList<>();
        componentScores.add(emergencyScore);
        if (insuranceScore != null) componentScores.add(insuranceScore);
        if (debtScore != null) componentScores.add(debtScore);
        if (savingsScore != null) componentScores.add(savingsScore);
        int overallScore = (int) Math.round(componentScores.stream().mapToInt(Integer::intValue).average().orElse(0));

        model.addAttribute("emergencyMonths", emergencyMonths);
        model.addAttribute("emergencyScore", emergencyScore);
        model.addAttribute("insuranceScore", insuranceScore);
        model.addAttribute("debtScore", debtScore);
        model.addAttribute("savingsScore", savingsScore);
        model.addAttribute("overallScore", overallScore);
        return "dashboard/health-score";
    }

    @PostMapping("/dashboard/health-score")
    public String saveHealthInputs(@RequestParam BigDecimal monthlyExpense,
                                   @RequestParam(required = false) BigDecimal monthlySavings,
                                   HttpServletRequest request, RedirectAttributes ra) {
        User owner = user(request);
        if (monthlyExpense.signum() <= 0) {
            ra.addFlashAttribute("error", "Monthly expense must be positive.");
            return "redirect:/dashboard/health-score";
        }
        com.moneymap.model.FinancialHealthInputs inputs = db.financialHealthInputs
                .findWhere(x -> owner.getId().equals(x.getOwnerId())).stream().findFirst()
                .orElseGet(com.moneymap.model.FinancialHealthInputs::new);
        inputs.setOwnerId(owner.getId());
        inputs.setMonthlyExpense(monthlyExpense);
        inputs.setMonthlySavings(monthlySavings);
        db.financialHealthInputs.save(inputs);
        return "redirect:/dashboard/health-score";
    }

    /** Linear 0-100 score: at or below `poor` is 0, at or above `good` is 100, straight-line between. */
    private int scoreBand(double value, double poor, double good) {
        if (value <= poor) return (int) Math.round(100.0 * value / poor / 2);   // partial credit below the floor
        if (value >= good) return 100;
        return (int) Math.round(50 + 50.0 * (value - poor) / (good - poor));
    }

    private BigDecimal activeLoansMonthlyEmi(String uid) {
        return db.loans.findWhere(l -> uid.equals(l.getOwnerId()) && !"CREDIT_CARD".equals(l.getLoanType()))
                .stream().map(Loan::getEmiAmount).map(Valuation::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
