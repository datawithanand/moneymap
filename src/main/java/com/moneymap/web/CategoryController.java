package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.model.asset.EquityHolding;
import com.moneymap.model.asset.HealthInsurance;
import com.moneymap.model.asset.LicPolicy;
import com.moneymap.model.asset.Loan;
import com.moneymap.model.asset.TermInsurance;
import com.moneymap.module.AssetService;
import com.moneymap.module.ModuleDef;
import com.moneymap.module.ModuleRegistry;
import com.moneymap.repository.Db;
import com.moneymap.repository.HouseholdMemberRepository;
import com.moneymap.service.PortfolioAggregationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified category pages matching the CashBanking / Investments / Retirement / Insurance /
 * RealEstate / Loans mockups: one KPI strip + pill-tabs + shared table per asset/liability
 * category, wired to the existing generic asset modules and their real slide-over drawers.
 */
@Controller
@RequestMapping("/categories")
public class CategoryController {

    /** One pill-tab. modulePath null means the mockup's tab has no dedicated real module yet. */
    private record Tab(String key, String label, String modulePath) {}

    private final ModuleRegistry registry;
    private final AssetService assetService;
    private final HouseholdMemberRepository householdMembers;
    private final PortfolioAggregationService aggregationService;
    private final Db db;

    public CategoryController(ModuleRegistry registry, AssetService assetService,
                              HouseholdMemberRepository householdMembers,
                              PortfolioAggregationService aggregationService, Db db) {
        this.registry = registry;
        this.assetService = assetService;
        this.householdMembers = householdMembers;
        this.aggregationService = aggregationService;
        this.db = db;
    }

    private User user(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    @GetMapping("/cash-banking")
    public String cashBanking(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("savings", "Savings Accounts", "savings-accounts"),
                new Tab("cash", "Cash in Hand", "cash-in-hand"),
                new Tab("fd", "Fixed Deposits", "fixed-deposits"),
                new Tab("rd", "Recurring Deposits", "recurring-deposits"));
        Map<String, BigDecimal> totals = moduleTotals(user, "savings-accounts", "cash-in-hand", "fixed-deposits", "recurring-deposits");
        List<Map<String, Object>> kpis = List.of(
                kpi("Savings", totals.get("savings-accounts")),
                kpi("Cash in hand", totals.get("cash-in-hand")),
                kpi("Fixed deposits", totals.get("fixed-deposits")),
                kpi("Recurring deposits", totals.get("recurring-deposits")));
        String note = "iWISH / Flexi RD accounts aren't shown in this table (they use a deposit-ledger form) — "
                + "manage them on the dedicated <a href=\"/assets/flexi-rds\">Flexi RD page</a>.";
        return renderCategory(request, model, "cash-banking", "Cash & Banking",
                "Savings, cash in hand, fixed and recurring deposits.", tabs, tab, kpis, "rd", note);
    }

    @GetMapping("/investments")
    public String investments(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("mf", "Mutual Funds", "mutual-funds"),
                new Tab("stocks", "Stocks", "stocks"),
                new Tab("etf", "ETFs", "stocks"),
                new Tab("bonds", "Bonds", "bonds"),
                new Tab("crypto", "Crypto", "crypto"),
                new Tab("esop", "ESOPs/RSUs", "esops"));

        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal current = BigDecimal.ZERO;
        for (Map<String, Object> row : assetService.rows(registry.byPath("mutual-funds"), user.getId(), null, user)) {
            invested = invested.add(nz(row.get("invested")));
            current = current.add(nz(row.get("currentValue")));
        }
        for (Map<String, Object> row : assetService.rows(registry.byPath("stocks"), user.getId(), null, user)) {
            invested = invested.add(nz(row.get("invested")));
            current = current.add(nz(row.get("currentValue")));
        }
        for (Map<String, Object> row : assetService.rows(registry.byPath("crypto"), user.getId(), null, user)) {
            invested = invested.add(nz(row.get("invested")));
            current = current.add(nz(row.get("currentValue")));
        }
        Map<String, BigDecimal> bondTotals = moduleTotals(user, "bonds");
        current = current.add(bondTotals.get("bonds"));
        invested = invested.add(bondTotals.get("bonds"));
        for (Map<String, Object> row : assetService.rows(registry.byPath("esops"), user.getId(), null, user)) {
            current = current.add(nz(row.get("vestedValue")));
        }
        BigDecimal gainLoss = current.subtract(invested);
        BigDecimal returnPct = invested.signum() == 0 ? BigDecimal.ZERO
                : gainLoss.multiply(BigDecimal.valueOf(100)).divide(invested, 1, java.math.RoundingMode.HALF_UP);
        List<Map<String, Object>> kpis = List.of(
                kpi("Invested", invested), kpi("Current value", current),
                kpi("Gain / loss", gainLoss), kpiPercent("Overall return", returnPct));

        return renderCategory(request, model, "investments", "Investments",
                "Mutual funds, stocks, ETFs, bonds, crypto, ESOPs/RSUs.", tabs, tab, kpis, "mf", null);
    }

    @GetMapping("/retirement")
    public String retirement(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("epf", "EPF / VPF", "pf"),
                new Tab("ppf", "PPF", "ppf"),
                new Tab("nps", "NPS", "nps"),
                new Tab("gratuity", "Gratuity", "gratuity"),
                new Tab("govt", "Govt. Schemes", "government-schemes"));
        Map<String, BigDecimal> totals = moduleTotals(user, "ppf", "nps", "gratuity", "government-schemes");
        List<Map<String, Object>> kpis = List.of(
                kpi("EPF/VPF", null), kpi("PPF", totals.get("ppf")), kpi("NPS", totals.get("nps")),
                kpi("Gratuity", totals.get("gratuity")), kpi("Govt. schemes", totals.get("government-schemes")));
        return renderCategory(request, model, "retirement", "Retirement",
                "EPF/VPF, PPF, NPS, gratuity and government schemes.", tabs, tab, kpis, "ppf", null);
    }

    @GetMapping("/insurance")
    public String insurance(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("term", "Term Life", "term-insurance"),
                new Tab("health", "Health", "health-insurance"),
                new Tab("lic", "LIC/Endowment/ULIP", "lic-policies"),
                new Tab("other", "Other Policies", null));

        BigDecimal termCover = BigDecimal.ZERO;
        BigDecimal annualPremium = BigDecimal.ZERO;
        int activePolicies = 0;
        for (TermInsurance t : db.termInsurance.findWhere(r -> user.getId().equals(r.getOwnerId()))) {
            termCover = termCover.add(nz(t.getSumAssured()));
            annualPremium = annualPremium.add(annualize(t.getPremiumAmount(), t.getPremiumFrequency()));
            activePolicies++;
        }
        BigDecimal healthCover = BigDecimal.ZERO;
        for (HealthInsurance h : db.healthInsurance.findWhere(r -> user.getId().equals(r.getOwnerId()))) {
            healthCover = healthCover.add(nz(h.getSumInsured()));
            annualPremium = annualPremium.add(annualize(h.getPremiumAmount(), h.getPremiumFrequency()));
            activePolicies++;
        }
        for (LicPolicy l : db.licPolicies.findWhere(r -> user.getId().equals(r.getOwnerId()))) {
            annualPremium = annualPremium.add(annualize(l.getPremiumAmount(), l.getPremiumFrequency()));
            activePolicies++;
        }
        List<Map<String, Object>> kpis = List.of(
                kpi("Term cover", termCover), kpi("Health cover", healthCover),
                kpi("Annual premium", annualPremium), kpiCount("Active policies", activePolicies));
        return renderCategory(request, model, "insurance", "Insurance",
                "Term, health, LIC/endowment/ULIP with surrender value.", tabs, tab, kpis, "term", null);
    }

    @GetMapping("/real-estate")
    public String realEstate(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("property", "Property", "real-estate"),
                new Tab("gold", "Gold", "gold"),
                new Tab("other", "Other Physical", "physical-assets"));
        Map<String, BigDecimal> totals = moduleTotals(user, "real-estate", "gold", "physical-assets");
        List<Map<String, Object>> kpis = List.of(
                kpi("Property value", totals.get("real-estate")), kpi("Gold value", totals.get("gold")),
                kpi("Other physical", totals.get("physical-assets")));
        return renderCategory(request, model, "real-estate", "Real Estate & Physical",
                "Property, gold by purity, and other physical assets.", tabs, tab, kpis, "property", null);
    }

    @GetMapping("/loans")
    public String loans(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("home", "Home Loan", "loans"),
                new Tab("personal", "Personal Loan", "loans"),
                new Tab("vehicle", "Vehicle Loan", "loans"),
                new Tab("education", "Education Loan", "loans"),
                new Tab("cards", "Credit Cards", "loans"));

        BigDecimal outstandingLoans = BigDecimal.ZERO;
        BigDecimal monthlyEmi = BigDecimal.ZERO;
        BigDecimal ccOutstanding = BigDecimal.ZERO;
        for (Loan l : db.loans.findWhere(r -> user.getId().equals(r.getOwnerId()))) {
            if ("CREDIT_CARD".equals(l.getLoanType())) {
                ccOutstanding = ccOutstanding.add(nz(l.getOutstandingBalance()));
            } else {
                outstandingLoans = outstandingLoans.add(nz(l.getOutstandingBalance()));
                monthlyEmi = monthlyEmi.add(nz(l.getEmiAmount()));
            }
        }
        List<Map<String, Object>> kpis = List.of(
                kpi("Outstanding loans", outstandingLoans), kpi("Monthly EMI", monthlyEmi),
                kpi("Credit card outstanding", ccOutstanding));
        return renderCategory(request, model, "loans", "Loans & Credit Cards",
                "Home, personal, vehicle & education loans, credit cards.", tabs, tab, kpis, "home", null);
    }

    // ── shared rendering ─────────────────────────────────────────────────

    private String renderCategory(HttpServletRequest request, Model model, String categoryKey, String title,
                                  String description, List<Tab> tabs, String requestedTab,
                                  List<Map<String, Object>> kpis, String defaultTab, String activeTabNote) {
        User user = user(request);
        String activeKey = requestedTab != null && tabs.stream().anyMatch(t -> t.key().equals(requestedTab))
                ? requestedTab : defaultTab;
        Tab active = tabs.stream().filter(t -> t.key().equals(activeKey)).findFirst().orElse(tabs.get(0));

        List<Map<String, Object>> tabModels = new ArrayList<>();
        for (Tab t : tabs) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("key", t.key());
            tm.put("label", t.label());
            tm.put("active", t.key().equals(active.key()));
            tabModels.add(tm);
        }

        ModuleDef<?> activeModule = active.modulePath() == null ? null : registry.byPath(active.modulePath());
        Map<String, String> labels = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean genericCrud = false;
        if (activeModule != null && !activeModule.listColumns.isEmpty()) {
            for (String c : activeModule.listColumns) labels.put(c, assetService.labelFor(activeModule, c));
            genericCrud = activeModule.genericCrud;
            if (genericCrud) {
                if ("loans".equals(activeModule.path)) {
                    String loanType = loanTypeFor(active.key());
                    for (Map<String, Object> row : assetService.rows(activeModule, user.getId(), null, user)) {
                        if (loanType.equals(row.get("loanType"))) rows.add(row);
                    }
                } else if ("stocks".equals(activeModule.path)) {
                    boolean wantEtf = "etf".equals(active.key());
                    for (EquityHolding eh : db.equityHoldings.findWhere(r -> user.getId().equals(r.getOwnerId()))) {
                        if (eh.getIsEtf() == wantEtf) rows.add(assetService.row(activeModule, eh, user));
                    }
                } else {
                    rows = assetService.rows(activeModule, user.getId(), null, user);
                }
            }
        }

        model.addAttribute("categoryKey", categoryKey);
        model.addAttribute("categoryTitle", title);
        model.addAttribute("categoryDescription", description);
        model.addAttribute("tabs", tabModels);
        model.addAttribute("activeTabKey", active.key());
        model.addAttribute("activeTabLabel", active.label());
        model.addAttribute("activeModule", activeModule);
        model.addAttribute("activeModulePath", active.modulePath());
        model.addAttribute("genericCrud", genericCrud);
        model.addAttribute("newUrlLoanType", "loans".equals(active.modulePath()) ? loanTypeFor(active.key()) : null);
        model.addAttribute("labels", labels);
        model.addAttribute("rows", rows);
        model.addAttribute("kpis", kpis);
        model.addAttribute("categoryNote", activeTabNote != null && active.key().equals("rd") ? activeTabNote : null);
        model.addAttribute("household", householdMembers.findByOwnerId(user.getId()));
        return "assets/category";
    }

    private String loanTypeFor(String tabKey) {
        return switch (tabKey) {
            case "home" -> "HOME_LOAN";
            case "personal" -> "PERSONAL_LOAN";
            case "vehicle" -> "CAR_LOAN";
            case "education" -> "EDUCATION_LOAN";
            case "cards" -> "CREDIT_CARD";
            default -> "OTHER";
        };
    }

    private Map<String, BigDecimal> moduleTotals(User user, String... paths) {
        var summary = aggregationService.aggregate(user);
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (String p : paths) out.put(p, BigDecimal.ZERO);
        for (var ms : summary.moduleSummaries) {
            if (out.containsKey(ms.path())) out.put(ms.path(), ms.total());
        }
        return out;
    }

    private BigDecimal annualize(BigDecimal amount, String frequency) {
        if (amount == null) return BigDecimal.ZERO;
        int multiplier = switch (frequency == null ? "" : frequency) {
            case "MONTHLY" -> 12;
            case "QUARTERLY" -> 4;
            default -> 1;
        };
        return amount.multiply(BigDecimal.valueOf(multiplier));
    }

    private BigDecimal nz(Object v) {
        return v instanceof BigDecimal b ? b : BigDecimal.ZERO;
    }

    private Map<String, Object> kpi(String label, BigDecimal value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value == null ? BigDecimal.ZERO : value);
        m.put("type", "money");
        return m;
    }

    private Map<String, Object> kpiPercent(String label, BigDecimal value) {
        Map<String, Object> m = kpi(label, value);
        m.put("type", "percent");
        return m;
    }

    private Map<String, Object> kpiCount(String label, int value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("type", "count");
        return m;
    }
}
