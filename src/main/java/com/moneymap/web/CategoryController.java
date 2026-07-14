package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.model.asset.*;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified category pages matching the CashBanking / Investments / Retirement / Insurance /
 * RealEstate / Loans mockups: one KPI strip + pill-tabs + a fixed 4-column table (name+sub,
 * two fields, one optionally colour-coded) per asset/liability category, wired to real data
 * and the existing generic asset modules' slide-over drawers.
 */
@Controller
@RequestMapping("/categories")
public class CategoryController {

    /** One pill-tab. modulePath null means the mockup's tab has no dedicated real module yet. */
    private record Tab(String key, String label, String modulePath, List<String> columnLabels,
                       List<String> columnAlign) {
        Tab(String key, String label, String modulePath, List<String> columnLabels) {
            this(key, label, modulePath, columnLabels, List.of("left", "left", "right", "right"));
        }
    }

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
                new Tab("savings", "Savings Accounts", "savings-accounts", List.of("Account", "Bank", "Balance", "Interest")),
                new Tab("cash", "Cash in Hand", "cash-in-hand", List.of("Description", "Held by", "Amount", "Last counted")),
                new Tab("fd", "Fixed Deposits", "fixed-deposits", List.of("Fixed deposit", "Bank", "Principal", "Maturity value")),
                new Tab("rd", "Recurring Deposits", "recurring-deposits", List.of("Recurring deposit", "Bank", "Monthly instalment", "Accrued value")));
        Map<String, BigDecimal> totals = moduleTotals(user, "savings-accounts", "cash-in-hand", "fixed-deposits", "recurring-deposits");
        List<Map<String, Object>> kpis = List.of(
                kpi("Savings", totals.get("savings-accounts")),
                kpi("Cash in hand", totals.get("cash-in-hand")),
                kpi("Fixed deposits", totals.get("fixed-deposits")),
                kpi("Recurring deposits", totals.get("recurring-deposits")));
        String note = "iWISH / Flexi RD accounts aren't shown in this table (they use a deposit-ledger form) — "
                + "manage them on the dedicated <a href=\"/assets/flexi-rds\">Flexi RD page</a>.";
        return renderCategory(request, model, "cash-banking", "Cash & Banking",
                "Savings, cash in hand, fixed and recurring deposits.", tabs, tab, kpis, "rd", note,
                key -> cashBankingRows(user, key));
    }

    private List<Map<String, Object>> cashBankingRows(User user, String tabKey) {
        return switch (tabKey) {
            case "savings" -> db.savingsAccounts.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), accountTypeLabel(r.getAccountType()), mask(r.getAccountNumber()),
                            r.getBankName(), r.getCurrentBalance(), pct(r.getInterestRate())))
                    .toList();
            case "cash" -> db.cashInHand.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), r.getLabel(), "", r.getFamilyMemberTag(), r.getAmount(),
                            r.getUpdatedAt() == null ? null : r.getUpdatedAt().toString().substring(0, 10)))
                    .toList();
            case "fd" -> db.fixedDeposits.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), r.getFdNumber() != null && !r.getFdNumber().isBlank() ? r.getFdNumber() : "Fixed Deposit",
                            r.getMaturityDate() == null ? "" : "Matures " + r.getMaturityDate(),
                            r.getBankName(), r.getPrincipalAmount(), fdMaturityValue(r)))
                    .toList();
            case "rd" -> db.recurringDeposits.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), r.getBankName(),
                            (r.getTenureMonths() == null ? "" : r.getTenureMonths() + " months")
                                    + (r.getStartDate() == null ? "" : " · started " + r.getStartDate()),
                            r.getAccountNumber(), r.getMonthlyInstallmentAmount(), rdCurrentValue(r)))
                    .toList();
            default -> List.of();
        };
    }

    @GetMapping("/investments")
    public String investments(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("mf", "Mutual Funds", "mutual-funds", List.of("Fund", "Category", "Invested", "Gain/Loss")),
                new Tab("stocks", "Stocks", "stocks", List.of("Stock", "Exchange", "Invested", "Gain/Loss")),
                new Tab("etf", "ETFs", "stocks", List.of("ETF", "Type", "Invested", "Gain/Loss")),
                new Tab("bonds", "Bonds", "bonds", List.of("Bond", "Issuer type", "Invested", "Yield")),
                new Tab("crypto", "Crypto", "crypto", List.of("Asset", "Exchange", "Invested", "Gain/Loss")),
                new Tab("esop", "ESOPs/RSUs", "esops", List.of("Grant", "Company", "Vested value", "Status")));

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
                : gainLoss.multiply(BigDecimal.valueOf(100)).divide(invested, 1, RoundingMode.HALF_UP);
        List<Map<String, Object>> kpis = List.of(
                kpi("Invested", invested), kpi("Current value", current),
                kpi("Gain / loss", gainLoss), kpiPercent("Overall return", returnPct));

        return renderCategory(request, model, "investments", "Investments",
                "Mutual funds, stocks, ETFs, bonds, crypto, ESOPs/RSUs.", tabs, tab, kpis, "mf", null,
                key -> investmentRows(user, key));
    }

    private List<Map<String, Object>> investmentRows(User user, String tabKey) {
        return switch (tabKey) {
            case "stocks", "etf" -> {
                boolean wantEtf = "etf".equals(tabKey);
                yield db.equityHoldings.findWhere(r -> user.getId().equals(r.getOwnerId()) && r.getIsEtf() == wantEtf).stream()
                        .map(r -> {
                            Map<String, Object> computed = assetService.row(registry.byPath("stocks"), r, user);
                            String col1 = wantEtf ? (r.getSector() != null ? r.getSector() : "ETF") : r.getExchange();
                            return colored(row(r.getId(), r.getStockName(),
                                    r.getQuantity() == null ? "" : r.getQuantity() + " units",
                                    col1, computed.get("invested"), computed.get("gainLoss")));
                        }).toList();
            }
            case "bonds" -> db.bonds.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), r.getIssuerName(),
                            r.getMaturityDate() == null ? "" : "Matures " + r.getMaturityDate(),
                            r.getBondType(), bondValue(r), pct(r.getCouponRate())))
                    .toList();
            case "crypto" -> db.cryptoHoldings.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> {
                        Map<String, Object> computed = assetService.row(registry.byPath("crypto"), r, user);
                        return colored(row(r.getId(), r.getCoinName(),
                                r.getQuantity() == null ? "" : r.getQuantity() + " " + r.getCoinSymbol(),
                                r.getExchangeOrWallet(), computed.get("invested"), computed.get("gainLoss")));
                    }).toList();
            case "esop" -> db.esops.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> {
                        Map<String, Object> computed = assetService.row(registry.byPath("esops"), r, user);
                        BigDecimal vestPct = r.getTotalUnitsGranted() == null || r.getTotalUnitsGranted().signum() == 0
                                ? null : nz(r.getVestedUnits()).multiply(BigDecimal.valueOf(100))
                                .divide(r.getTotalUnitsGranted(), 0, RoundingMode.HALF_UP);
                        return row(r.getId(), r.getGrantType() + " Grant",
                                r.getTotalUnitsGranted() == null ? "" : r.getTotalUnitsGranted() + " units",
                                r.getCompanyName(), computed.get("vestedValue"),
                                vestPct == null ? "—" : vestPct + "% vested");
                    }).toList();
            default -> List.of();
        };
    }

    @GetMapping("/retirement")
    public String retirement(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("epf", "EPF / VPF", "pf", List.of("Account", "Employer", "Balance", "Monthly contribution")),
                new Tab("ppf", "PPF", "ppf", List.of("Account", "Bank/PO", "Balance", "Yearly contribution")),
                new Tab("nps", "NPS", "nps", List.of("Account", "Tier", "Balance", "Monthly contribution")),
                new Tab("gratuity", "Gratuity", "gratuity", List.of("Employer", "Years of service", "Estimated gratuity", "Vesting")),
                new Tab("govt", "Govt. Schemes", "government-schemes", List.of("Scheme", "Holder", "Balance", "Maturity")));
        Map<String, BigDecimal> totals = moduleTotals(user, "ppf", "nps", "gratuity", "government-schemes");
        List<Map<String, Object>> kpis = List.of(
                kpi("EPF/VPF", null), kpi("PPF", totals.get("ppf")), kpi("NPS", totals.get("nps")),
                kpi("Gratuity", totals.get("gratuity")), kpi("Govt. schemes", totals.get("government-schemes")));
        return renderCategory(request, model, "retirement", "Retirement",
                "EPF/VPF, PPF, NPS, gratuity and government schemes.", tabs, tab, kpis, "ppf", null,
                key -> retirementRows(user, key));
    }

    private List<Map<String, Object>> retirementRows(User user, String tabKey) {
        return switch (tabKey) {
            case "ppf" -> db.ppfAccounts.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), "PPF Account", r.getOpeningDate() == null ? "" : "Opened " + r.getOpeningDate(),
                            r.getBankOrPostOffice(), r.getCurrentBalance(), r.getYearlyContribution()))
                    .toList();
            case "nps" -> db.npsAccounts.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), "NPS " + tierLabel(r.getTier()),
                            r.getPran() == null ? "" : "PRAN " + r.getPran(),
                            tierLabel(r.getTier()), r.getCurrentCorpus(), null))
                    .toList();
            case "gratuity" -> db.gratuities.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), r.getEmployerName(), "",
                            r.getYearsOfService() == null ? "" : r.getYearsOfService() + " years",
                            r.getExpectedGratuityAmount(), r.getIncludeInNetWorth() ? "Vested" : "Not counted"))
                    .toList();
            case "govt" -> db.governmentSchemes.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), schemeLabel(r), r.getAccountNumber(),
                            r.getFamilyMemberTag(), r.getCurrentBalance(), r.getMaturityDate()))
                    .toList();
            default -> List.of();
        };
    }

    @GetMapping("/insurance")
    public String insurance(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("term", "Term Life", "term-insurance", List.of("Policy", "Insurer", "Sum assured", "Premium/yr")),
                new Tab("health", "Health", "health-insurance", List.of("Policy", "Insurer", "Sum insured", "Premium/yr")),
                new Tab("lic", "LIC/Endowment/ULIP", "lic-policies", List.of("Policy", "Type", "Sum assured", "Surrender value")),
                new Tab("other", "Other Policies", null, List.of("Policy", "Type", "Sum insured", "Premium/yr")));

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
                "Term, health, LIC/endowment/ULIP with surrender value.", tabs, tab, kpis, "term", null,
                key -> insuranceRows(user, key));
    }

    private List<Map<String, Object>> insuranceRows(User user, String tabKey) {
        return switch (tabKey) {
            case "term" -> db.termInsurance.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), r.getInsurerName() + " Term Plan", "Policy no. " + r.getPolicyNumber(),
                            r.getInsurerName(), r.getSumAssured(), annualize(r.getPremiumAmount(), r.getPremiumFrequency())))
                    .toList();
            case "health" -> db.healthInsurance.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), policyTypeLabel(r.getPolicyType()),
                            r.getMembersCovered() != null && !r.getMembersCovered().isBlank()
                                    ? r.getMembersCovered() : "Policy no. " + r.getPolicyNumber(),
                            r.getInsurerName(), r.getSumInsured(), annualize(r.getPremiumAmount(), r.getPremiumFrequency())))
                    .toList();
            case "lic" -> db.licPolicies.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), r.getPolicyName(), "Policy no. " + r.getPolicyNumber(),
                            r.getPolicyType(), r.getSumAssured(), r.getSurrenderValue()))
                    .toList();
            default -> List.of();
        };
    }

    @GetMapping("/real-estate")
    public String realEstate(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("property", "Property", "real-estate", List.of("Property", "Location", "Purchase value", "Current value")),
                new Tab("gold", "Gold", "gold", List.of("Item", "Purity", "Weight", "Current value")),
                new Tab("other", "Other Physical", "physical-assets", List.of("Item", "Category", "Purchase value", "Current value")));
        Map<String, BigDecimal> totals = moduleTotals(user, "real-estate", "gold", "physical-assets");
        List<Map<String, Object>> kpis = List.of(
                kpi("Property value", totals.get("real-estate")), kpi("Gold value", totals.get("gold")),
                kpi("Other physical", totals.get("physical-assets")));
        return renderCategory(request, model, "real-estate", "Real Estate & Physical",
                "Property, gold by purity, and other physical assets.", tabs, tab, kpis, "property", null,
                key -> realEstateRows(user, key));
    }

    private List<Map<String, Object>> realEstateRows(User user, String tabKey) {
        return switch (tabKey) {
            case "property" -> db.realEstate.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), r.getPropertyName(), propertyTypeLabel(r.getPropertyType()),
                            joinNonBlank(r.getCity(), r.getState()), r.getPurchaseValue(), r.getCurrentEstimatedValue()))
                    .toList();
            case "gold" -> db.goldHoldings.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> {
                        Map<String, Object> computed = assetService.row(registry.byPath("gold"), r, user);
                        Object weight = r.getWeightGrams() != null ? r.getWeightGrams() + "g"
                                : (r.getUnits() != null ? r.getUnits() + " units" : "—");
                        return row(r.getId(), r.getLabel(), r.getPurity() != null ? r.getPurity() : goldTypeLabel(r.getGoldType()),
                                weight, null, computed.get("currentValue"));
                    }).toList();
            case "other" -> db.physicalAssets.findWhere(r -> user.getId().equals(r.getOwnerId())).stream()
                    .map(r -> row(r.getId(), r.getAssetName(), "", assetTypeLabel(r.getAssetType()),
                            r.getPurchaseValue(), r.getCurrentEstimatedValue()))
                    .toList();
            default -> List.of();
        };
    }

    @GetMapping("/loans")
    public String loans(@RequestParam(required = false) String tab, HttpServletRequest request, Model model) {
        User user = user(request);
        List<Tab> tabs = List.of(
                new Tab("home", "Home Loan", "loans", List.of("Loan", "Lender", "Outstanding", "EMI/mo")),
                new Tab("personal", "Personal Loan", "loans", List.of("Loan", "Lender", "Outstanding", "EMI/mo")),
                new Tab("vehicle", "Vehicle Loan", "loans", List.of("Loan", "Lender", "Outstanding", "EMI/mo")),
                new Tab("education", "Education Loan", "loans", List.of("Loan", "Lender", "Outstanding", "EMI/mo")),
                new Tab("cards", "Credit Cards", "loans", List.of("Card", "Bank", "Outstanding", "Due date")));

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
                "Home, personal, vehicle & education loans, credit cards.", tabs, tab, kpis, "home", null,
                key -> loanRows(user, key));
    }

    private List<Map<String, Object>> loanRows(User user, String tabKey) {
        String loanType = loanTypeFor(tabKey);
        List<Loan> loans = db.loans.findWhere(r -> user.getId().equals(r.getOwnerId()) && loanType.equals(r.getLoanType()));
        if ("cards".equals(tabKey)) {
            return loans.stream().map(r -> row(r.getId(), "Credit Card",
                    r.getLoanAccountNumber() == null ? "" : "Card •• " + last4(r.getLoanAccountNumber()),
                    r.getLenderName(), r.getOutstandingBalance(), r.getPaymentDueDate())).toList();
        }
        return loans.stream().map(r -> row(r.getId(), loanTypeLabel(loanType),
                r.getTenureMonths() == null ? "" : monthsRemainingLabel(r),
                r.getLenderName(), r.getOutstandingBalance(), r.getEmiAmount())).toList();
    }

    // ── shared rendering ─────────────────────────────────────────────────

    private String renderCategory(HttpServletRequest request, Model model, String categoryKey, String title,
                                  String description, List<Tab> tabs, String requestedTab,
                                  List<Map<String, Object>> kpis, String defaultTab, String activeTabNote,
                                  java.util.function.Function<String, List<Map<String, Object>>> rowSupplier) {
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
        // Mutual Funds has its own dedicated page (MutualFunds.dc.html) — link out to it instead
        // of embedding a table here, exactly like the mockup's own sidebar treats it as a
        // separate destination rather than an inline tab table.
        boolean genericCrud = activeModule != null && activeModule.genericCrud && !activeModule.listColumns.isEmpty()
                && !"mutual-funds".equals(active.modulePath());
        List<Map<String, Object>> rows = genericCrud ? rowSupplier.apply(active.key()) : List.of();

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
        model.addAttribute("columnLabels", active.columnLabels());
        model.addAttribute("columnAlign", active.columnAlign());
        model.addAttribute("rows", rows);
        model.addAttribute("kpis", kpis);
        model.addAttribute("categoryNote", activeTabNote != null && active.key().equals("rd") ? activeTabNote : null);
        model.addAttribute("household", householdMembers.findByOwnerId(user.getId()));
        return "assets/category";
    }

    // ── row-building helpers ─────────────────────────────────────────────

    private Map<String, Object> row(String id, Object c0, Object sub, Object c1, Object c2, Object c3) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("c0", c0);
        m.put("sub", sub);
        m.put("c1", c1);
        m.put("c2", c2);
        m.put("c3", c3);
        return m;
    }

    private Map<String, Object> colored(Map<String, Object> row) {
        Object c3 = row.get("c3");
        boolean negative = c3 instanceof BigDecimal b && b.signum() < 0;
        row.put("c3Color", negative ? "var(--pill-down-fg)" : "var(--pill-up-fg)");
        return row;
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

    private String loanTypeLabel(String loanType) {
        return switch (loanType) {
            case "HOME_LOAN" -> "Home Loan";
            case "PERSONAL_LOAN" -> "Personal Loan";
            case "CAR_LOAN" -> "Vehicle Loan";
            case "EDUCATION_LOAN" -> "Education Loan";
            default -> "Loan";
        };
    }

    private String monthsRemainingLabel(Loan l) {
        if (l.getTenureMonths() == null || l.getStartDate() == null) return "";
        long elapsed = java.time.temporal.ChronoUnit.MONTHS.between(
                l.getStartDate().withDayOfMonth(1), java.time.LocalDate.now().withDayOfMonth(1));
        long remaining = Math.max(0, l.getTenureMonths() - elapsed);
        return remaining + " months remaining";
    }

    private String accountTypeLabel(String accountType) {
        return "SALARY".equals(accountType) ? "Salary Account" : "Savings Account";
    }

    private String tierLabel(String tier) {
        return "TIER_1".equals(tier) ? "Tier I" : "TIER_2".equals(tier) ? "Tier II" : tier;
    }

    private String schemeLabel(GovernmentScheme r) {
        if ("OTHER".equals(r.getSchemeType()) && r.getSchemeName() != null && !r.getSchemeName().isBlank()) {
            return r.getSchemeName();
        }
        return switch (r.getSchemeType() == null ? "" : r.getSchemeType()) {
            case "SSY" -> "Sukanya Samriddhi Yojana";
            case "SCSS" -> "Senior Citizens Savings Scheme";
            case "KVP" -> "Kisan Vikas Patra";
            case "NSC" -> "National Savings Certificate";
            default -> "Government Scheme";
        };
    }

    private String policyTypeLabel(String policyType) {
        return "FAMILY_FLOATER".equals(policyType) ? "Family Floater" : "Individual Health Plan";
    }

    private String propertyTypeLabel(String type) {
        if (type == null) return "";
        return switch (type) {
            case "RESIDENTIAL_APARTMENT" -> "Residential Apartment";
            case "RESIDENTIAL_HOUSE" -> "Residential House";
            case "COMMERCIAL_OFFICE" -> "Commercial Office";
            case "COMMERCIAL_SHOP" -> "Commercial Shop";
            case "LAND" -> "Land";
            case "PLOT" -> "Plot";
            default -> "Other";
        };
    }

    private String goldTypeLabel(String type) {
        if (type == null) return "";
        return type.replace("_", " ");
    }

    private String assetTypeLabel(String type) {
        if (type == null) return "";
        return switch (type) {
            case "VEHICLE" -> "Vehicle";
            case "ART_COLLECTIBLE" -> "Art / Collectible";
            case "ELECTRONICS" -> "Electronics";
            case "JEWELLERY_OTHER" -> "Jewellery";
            default -> "Other";
        };
    }

    private String joinNonBlank(String a, String b) {
        if (a == null || a.isBlank()) return b == null ? "" : b;
        if (b == null || b.isBlank()) return a;
        return a + ", " + b;
    }

    private String mask(String number) {
        if (number == null || number.length() < 4) return "";
        return "A/c •• " + number.substring(number.length() - 4);
    }

    private String last4(String number) {
        if (number == null || number.length() < 4) return number == null ? "" : number;
        return number.substring(number.length() - 4);
    }

    private String pct(BigDecimal v) {
        return v == null ? "—" : v + "% p.a.";
    }

    private BigDecimal fdMaturityValue(FixedDeposit r) {
        if (r.getPrincipalAmount() == null || r.getInterestRate() == null
                || r.getStartDate() == null || r.getMaturityDate() == null) return nz(r.getPrincipalAmount());
        long days = java.time.temporal.ChronoUnit.DAYS.between(r.getStartDate(), r.getMaturityDate());
        BigDecimal years = BigDecimal.valueOf(days).divide(BigDecimal.valueOf(365), 6, RoundingMode.HALF_UP);
        double rate = r.getInterestRate().doubleValue() / 100.0;
        double maturity = r.getPrincipalAmount().doubleValue() * Math.pow(1 + rate / 4, 4 * years.doubleValue());
        return BigDecimal.valueOf(maturity).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal rdCurrentValue(RecurringDeposit r) {
        if (r.getMonthlyInstallmentAmount() == null || r.getStartDate() == null || r.getInterestRate() == null) {
            return BigDecimal.ZERO;
        }
        long monthsElapsed = Math.max(0, java.time.temporal.ChronoUnit.MONTHS.between(
                r.getStartDate().withDayOfMonth(1), java.time.LocalDate.now().withDayOfMonth(1)));
        if (r.getTenureMonths() != null) monthsElapsed = Math.min(monthsElapsed, r.getTenureMonths());
        double rate = r.getInterestRate().doubleValue() / 100.0;
        double total = 0;
        for (long m = 1; m <= monthsElapsed; m++) {
            double years = (monthsElapsed - m + 1) / 12.0;
            total += r.getMonthlyInstallmentAmount().doubleValue() * Math.pow(1 + rate / 4, 4 * years);
        }
        return BigDecimal.valueOf(total).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal bondValue(Bond r) {
        BigDecimal perUnit = r.getCurrentValuePerUnit() != null ? r.getCurrentValuePerUnit() : r.getFaceValue();
        return nz(perUnit).multiply(nz(r.getUnitsHeld()));
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
