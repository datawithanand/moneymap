package com.moneymap.module;

import com.moneymap.model.User;
import com.moneymap.model.asset.*;
import com.moneymap.module.Buckets.AllocationClass;
import com.moneymap.module.Buckets.Bucket;
import com.moneymap.repository.Db;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.BiFunction;

import static com.moneymap.module.FieldSpec.*;
import static com.moneymap.module.Valuation.*;

/**
 * The single authoritative registry of every asset module: schema fields (Section 17),
 * net worth bucket (each module's §h), and allocation split (Section 12's mapping table).
 */
@Component
public class ModuleRegistry {

    private final List<ModuleDef<?>> modules = new ArrayList<>();
    private final Map<String, ModuleDef<?>> byPath = new LinkedHashMap<>();

    private static final List<String> CCY = List.of("INR", "USD");

    public ModuleRegistry(Db db) {

        // ── Section 05 — Cash & Banking ─────────────────────────────────────
        add(new ModuleDef<>("savings-accounts", "Savings Accounts", "05 §1", db.savingsAccounts, SavingsAccount.class,
                Bucket.CASH,
                List.of(text("bankName", "Bank Name", true),
                        text("accountNumber", "Account Number", true, "Shown masked everywhere except this form"),
                        select("accountType", "Account Type", true, "SAVINGS", "SALARY"),
                        text("ifscCode", "IFSC Code", false, "e.g. HDFC0001234"),
                        num("currentBalance", "Current Balance", true, "Manually refreshed from your bank — the sole source of truth"),
                        num("interestRate", "Interest Rate %", false, "Informational only"),
                        select("currency", "Currency", true, "INR", "USD"),
                        check("isPrimary", "Primary account")),
                List.of("bankName", "accountType", "maskedNumber", "familyMemberTag", "currentBalance", "currency"),
                (r, u) -> convert(nz(r.getCurrentBalance()), r.getCurrency(), u),
                singleClass(AllocationClass.CASH),
                Map.of("maskedNumber", (r, u) -> mask(r.getAccountNumber())),
                true));

        add(new ModuleDef<>("cash-in-hand", "Cash in Hand", "05 §2", db.cashInHand, CashInHand.class,
                Bucket.CASH,
                List.of(text("label", "Label", true),
                        num("amount", "Amount", true),
                        text("location", "Location", false, "Private — never shared at any permission level"),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("label", "familyMemberTag", "amount", "currency"),
                (r, u) -> convert(nz(r.getAmount()), r.getCurrency(), u),
                singleClass(AllocationClass.CASH),
                Map.of(),
                true));

        add(new ModuleDef<>("fixed-deposits", "Fixed Deposits", "05 §3", db.fixedDeposits, FixedDeposit.class,
                Bucket.CASH,
                List.of(text("bankName", "Bank Name", true),
                        text("fdNumber", "FD Number", false),
                        num("principalAmount", "Principal Amount", true),
                        date("startDate", "Start Date", true),
                        date("maturityDate", "Maturity Date", true),
                        num("interestRate", "Interest Rate %", true),
                        select("payoutType", "Payout Type", true, "CUMULATIVE", "MONTHLY", "QUARTERLY", "ANNUAL"),
                        check("isTaxSaverFD", "Tax-saver FD (5-year lock-in, 80C)"),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("bankName", "fdNumber", "principalAmount", "interestRate", "maturityDate", "maturityValue", "daysToMaturity"),
                (r, u) -> convert(fdCurrentValue(r), r.getCurrency(), u),
                singleClass(AllocationClass.CASH),
                Map.of("maturityValue", (r, u) -> fdMaturityValue(r),
                        "daysToMaturity", (r, u) -> daysTo(r.getMaturityDate())),
                true));

        add(new ModuleDef<>("recurring-deposits", "Recurring Deposits", "05 §4", db.recurringDeposits, RecurringDeposit.class,
                Bucket.CASH,
                List.of(text("bankName", "Bank Name", true),
                        text("accountNumber", "Account Number", false),
                        num("monthlyInstallmentAmount", "Monthly Installment", true),
                        date("startDate", "Start Date", true),
                        num("tenureMonths", "Tenure (months)", true),
                        num("interestRate", "Interest Rate %", true),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("bankName", "monthlyInstallmentAmount", "tenureMonths", "interestRate", "currentValue", "maturityValue"),
                (r, u) -> convert(rdCurrentValue(r), r.getCurrency(), u),
                singleClass(AllocationClass.CASH),
                Map.of("currentValue", (r, u) -> rdCurrentValue(r),
                        "maturityValue", (r, u) -> rdMaturityValue(r)),
                true));

        add(new ModuleDef<>("flexi-rds", "iWISH / Flexi RD", "05 §5", db.flexiRds, FlexiRd.class,
                Bucket.CASH,
                List.of(),   // custom form (deposit ledger rows)
                List.of("bankName", "accountNumber", "totalDeposited", "currentValue", "targetMaturityDate"),
                (r, u) -> convert(flexiRdValue(r), r.getCurrency(), u),
                singleClass(AllocationClass.CASH),
                Map.of("totalDeposited", (r, u) -> r.totalDeposited(),
                        "currentValue", (r, u) -> flexiRdValue(r)),
                false));

        // ── Section 07 — Retirement ─────────────────────────────────────────
        add(new ModuleDef<>("pf", "Provident Fund (EPF/VPF)", "07 §6", db.pfAccounts, PfAccount.class,
                Bucket.RETIREMENT,
                List.of(),   // custom two-level UAN/employer UI
                List.of("uan", "familyMemberTag"),
                null,        // valued via child employer records in the aggregation service
                null,
                Map.of(),
                false));

        add(new ModuleDef<>("ppf", "PPF", "07 §7", db.ppfAccounts, PpfAccount.class,
                Bucket.RETIREMENT,
                List.of(text("accountNumber", "Account Number", true),
                        text("bankOrPostOffice", "Bank / Post Office", true),
                        date("openingDate", "Opening Date", true),
                        num("currentBalance", "Current Balance", true),
                        num("yearlyContribution", "Yearly Contribution", false, "Counts toward 80C (Old Regime)"),
                        num("interestRate", "Interest Rate %", true)),
                List.of("accountNumber", "bankOrPostOffice", "currentBalance", "yearlyContribution", "maturityDate"),
                (r, u) -> nz(r.getCurrentBalance()),
                singleClass(AllocationClass.DEBT),
                Map.of("maturityDate", (r, u) -> r.getOpeningDate() == null ? null : r.getOpeningDate().plusYears(15)),
                true));

        add(new ModuleDef<>("nps", "NPS", "07 §8", db.npsAccounts, NpsAccount.class,
                Bucket.RETIREMENT,
                List.of(text("pran", "PRAN", true, "12-digit Permanent Retirement Account Number"),
                        select("tier", "Tier", true, "TIER_1", "TIER_2"),
                        num("currentCorpus", "Current Corpus", true),
                        num("equityAllocationPercent", "Equity Allocation %", true),
                        num("corporateBondAllocationPercent", "Corporate Bond Allocation %", true),
                        num("govSecuritiesAllocationPercent", "Government Securities Allocation %", true),
                        text("pensionFundManager", "Pension Fund Manager", false)),
                List.of("pran", "tier", "currentCorpus", "equityAllocationPercent"),
                (r, u) -> nz(r.getCurrentCorpus()),
                (r, u) -> splitEquityDebt(null, r.getEquityAllocationPercent()),
                Map.of(),
                true));

        add(new ModuleDef<>("gratuity", "Gratuity", "07 §9", db.gratuities, Gratuity.class,
                Bucket.RETIREMENT,
                List.of(text("employerName", "Employer", true),
                        date("dateOfJoining", "Date of Joining", true),
                        num("lastDrawnSalary", "Last Drawn Salary (Basic+DA, monthly)", false),
                        num("yearsOfService", "Years of Service", false),
                        num("expectedGratuityAmount", "Expected Gratuity Amount", true),
                        check("includeInNetWorth", "Include in net worth")),
                List.of("employerName", "dateOfJoining", "expectedGratuityAmount", "includeInNetWorth"),
                (r, u) -> r.getIncludeInNetWorth() ? nz(r.getExpectedGratuityAmount()) : BigDecimal.ZERO,
                (r, u) -> r.getIncludeInNetWorth() ? Map.of(AllocationClass.DEBT, BigDecimal.ONE) : Map.of(),
                Map.of(),
                true));

        add(new ModuleDef<>("government-schemes", "Government Schemes", "07 §10", db.governmentSchemes, GovernmentScheme.class,
                Bucket.RETIREMENT,
                List.of(select("schemeType", "Scheme", true, "SSY", "SCSS", "KVP", "NSC", "OTHER"),
                        text("schemeName", "Scheme Name", false).withVisibleIf("schemeType=OTHER"),
                        text("accountNumber", "Account Number", true),
                        text("bankOrPostOffice", "Bank / Post Office", true),
                        date("openingDate", "Opening Date", true),
                        num("currentBalance", "Current Balance", true),
                        num("yearlyContribution", "Yearly Contribution", false),
                        num("interestRate", "Interest Rate %", true),
                        date("maturityDate", "Maturity Date", false)),
                List.of("schemeType", "accountNumber", "currentBalance", "interestRate", "maturityDate"),
                (r, u) -> nz(r.getCurrentBalance()),
                singleClass(AllocationClass.DEBT),
                Map.of(),
                true));

        // ── Section 06 — Investments ────────────────────────────────────────
        add(new ModuleDef<>("mutual-funds", "Mutual Funds", "06 §11", db.mutualFunds, MutualFund.class,
                Bucket.INVESTMENTS,
                List.of(text("fundName", "Fund Name", true),
                        text("amcName", "AMC / Fund House", true),
                        select("category", "Category", true, "LARGE_CAP", "MID_CAP", "SMALL_CAP", "FLEXI_CAP",
                                "MULTI_CAP", "HYBRID_AGGRESSIVE", "HYBRID_CONSERVATIVE", "HYBRID_BALANCED", "INDEX",
                                "SECTORAL_THEMATIC", "DEBT_LIQUID", "DEBT_SHORT_DURATION", "DEBT_MEDIUM_DURATION",
                                "DEBT_LONG_DURATION", "ELSS", "FUND_OF_FUNDS", "INTERNATIONAL", "OTHER"),
                        text("folioNumber", "Folio Number", true, "CAMS/KFin folio — the import dedup key"),
                        select("investmentType", "Investment Type", true, "SIP", "LUMPSUM", "BOTH"),
                        num("unitsHeld", "Units Held", true),
                        num("averageNavPerUnit", "Average NAV per Unit", true),
                        num("currentNavPerUnit", "Current NAV per Unit", true, "Enter today's NAV from your fund house app or AMFI"),
                        date("navAsOfDate", "NAV As Of", true),
                        num("sipAmount", "SIP Amount", false).withVisibleIf("investmentType=SIP|BOTH"),
                        num("sipDate", "SIP Date (day of month)", false).withVisibleIf("investmentType=SIP|BOTH"),
                        select("sipStatus", "SIP Status", false, "ACTIVE", "PAUSED", "STOPPED").withVisibleIf("investmentType=SIP|BOTH"),
                        check("isElss", "ELSS (80C)"),
                        date("elssLockInExpiryDate", "ELSS Lock-in Expiry", false).withVisibleIf("isElss=true"),
                        num("equityAllocationPercent", "Equity Allocation % (hybrid funds)", false)
                                .withVisibleIf("category=HYBRID_AGGRESSIVE|HYBRID_CONSERVATIVE|HYBRID_BALANCED"),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("fundName", "category", "folioNumber", "unitsHeld", "invested", "currentValue", "gainLoss", "xirr"),
                (r, u) -> convert(mfCurrentValue(r), r.getCurrency(), u),
                ModuleRegistry::mfAllocation,
                Map.of("invested", (r, u) -> mfInvested(r),
                        "currentValue", (r, u) -> mfCurrentValue(r),
                        "gainLoss", (r, u) -> mfCurrentValue(r).subtract(mfInvested(r)),
                        "xirr", (r, u) -> {
                            BigDecimal x = mfXirr(r, db.mutualFundTransactions.findWhere(
                                    t -> r.getId().equals(t.getMutualFundId())));
                            return x == null ? "—" : x + "%";
                        }),
                true));

        add(new ModuleDef<>("stocks", "Stocks & ETFs", "06 §12", db.equityHoldings, EquityHolding.class,
                Bucket.INVESTMENTS,
                List.of(text("stockName", "Stock / ETF Name", true),
                        text("tickerSymbol", "Ticker", true),
                        select("exchange", "Exchange", true, "NSE", "BSE", "NASDAQ", "NYSE", "OTHER"),
                        select("currency", "Currency", true, "INR", "USD"),
                        num("quantity", "Quantity", true),
                        num("averageBuyPrice", "Average Buy Price", true),
                        num("currentPrice", "Current Price", true),
                        date("priceAsOfDate", "Price As Of", true),
                        text("brokerName", "Broker", false),
                        select("sector", "Sector", false, "BANKING_FINANCE", "IT_TECHNOLOGY", "PHARMA_HEALTHCARE",
                                "FMCG", "AUTO", "INFRASTRUCTURE", "ENERGY", "METALS_MINING", "REAL_ESTATE_REIT",
                                "TELECOM", "MEDIA", "CHEMICALS", "CONSUMER_DISCRETIONARY", "OTHER"),
                        check("isEtf", "This is an ETF"),
                        select("allocationClassOverride", "Allocation Class Override", false,
                                "EQUITY", "DEBT", "GOLD", "REAL_ESTATE", "ALTERNATIVE")
                                .withHint("For ETFs whose default (Equity) would misrepresent them — gold/bond/REIT ETFs")),
                List.of("stockName", "tickerSymbol", "exchange", "quantity", "invested", "currentValue", "gainLoss"),
                (r, u) -> convert(equityValue(r), r.getCurrency(), u),
                (r, u) -> {
                    AllocationClass cls = r.getAllocationClassOverride() != null && !r.getAllocationClassOverride().isBlank()
                            ? AllocationClass.valueOf(r.getAllocationClassOverride()) : AllocationClass.EQUITY;
                    return Map.of(cls, BigDecimal.ONE);
                },
                Map.of("invested", (r, u) -> nz(r.getQuantity()).multiply(nz(r.getAverageBuyPrice())),
                        "currentValue", (r, u) -> equityValue(r),
                        "gainLoss", (r, u) -> equityValue(r).subtract(nz(r.getQuantity()).multiply(nz(r.getAverageBuyPrice())))),
                true));

        add(new ModuleDef<>("bonds", "Bonds & Debentures", "06 §13", db.bonds, Bond.class,
                Bucket.INVESTMENTS,
                List.of(text("issuerName", "Issuer", true),
                        select("bondType", "Type", true, "GOVERNMENT", "CORPORATE", "TAX_FREE", "SGBS", "OTHER"),
                        text("isinCode", "ISIN", false, "The import dedup key"),
                        num("faceValue", "Face Value per Unit", true),
                        num("unitsHeld", "Units Held", true),
                        num("purchasePrice", "Purchase Price per Unit", true),
                        num("couponRate", "Coupon Rate %", true),
                        select("couponFrequency", "Coupon Frequency", true, "ANNUAL", "SEMI_ANNUAL", "QUARTERLY", "MONTHLY", "AT_MATURITY", "NONE"),
                        date("issueDate", "Issue Date", true),
                        date("maturityDate", "Maturity Date", true),
                        num("currentValuePerUnit", "Current Value per Unit", false, "Leave blank to use face value"),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("issuerName", "bondType", "unitsHeld", "couponRate", "maturityDate", "currentValue"),
                (r, u) -> convert(bondValue(r), r.getCurrency(), u),
                singleClass(AllocationClass.DEBT),
                Map.of("currentValue", (r, u) -> bondValue(r)),
                true));

        add(new ModuleDef<>("crypto", "Cryptocurrency", "06 §14", db.cryptoHoldings, CryptoHolding.class,
                Bucket.INVESTMENTS,
                List.of(text("coinName", "Coin Name", true),
                        text("coinSymbol", "Symbol", true),
                        text("exchangeOrWallet", "Exchange / Wallet", true),
                        num("quantity", "Quantity", true),
                        num("averageBuyPrice", "Average Buy Price", true),
                        num("currentPrice", "Current Price", true),
                        date("priceAsOfDate", "Price As Of", true),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("coinName", "coinSymbol", "quantity", "invested", "currentValue", "gainLoss"),
                (r, u) -> convert(cryptoValue(r), r.getCurrency(), u),
                singleClass(AllocationClass.ALTERNATIVE),
                Map.of("invested", (r, u) -> nz(r.getQuantity()).multiply(nz(r.getAverageBuyPrice())),
                        "currentValue", (r, u) -> cryptoValue(r),
                        "gainLoss", (r, u) -> cryptoValue(r).subtract(nz(r.getQuantity()).multiply(nz(r.getAverageBuyPrice())))),
                true));

        add(new ModuleDef<>("esops", "ESOPs / RSUs / ESPP", "06 §15", db.esops, Esop.class,
                Bucket.INVESTMENTS,
                List.of(text("companyName", "Company", true),
                        select("grantType", "Grant Type", true, "ESOP", "RSU", "ESPP"),
                        date("grantDate", "Grant Date", true),
                        num("totalUnitsGranted", "Total Units Granted", true),
                        num("vestedUnits", "Vested Units", true),
                        num("strikePrice", "Strike Price", false).withVisibleIf("grantType=ESOP"),
                        num("currentFmv", "Current FMV per Unit", true),
                        date("fmvAsOfDate", "FMV As Of", true),
                        text("vestingScheduleNote", "Vesting Schedule Note", false),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("companyName", "grantType", "totalUnitsGranted", "vestedUnits", "vestedValue", "unvestedValue"),
                (r, u) -> convert(esopVestedValue(r), r.getCurrency(), u),   // vested value only (§15)
                singleClass(AllocationClass.EQUITY),
                Map.of("vestedValue", (r, u) -> esopVestedValue(r),
                        "unvestedValue", (r, u) -> nz(r.getTotalUnitsGranted()).subtract(nz(r.getVestedUnits()))
                                .max(BigDecimal.ZERO).multiply(nz(r.getCurrentFmv()))),
                true));

        // ── Section 08 — Insurance ──────────────────────────────────────────
        add(new ModuleDef<>("term-insurance", "Term Insurance", "08 §18", db.termInsurance, TermInsurance.class,
                Bucket.NONE,
                List.of(text("insurerName", "Insurer", true),
                        text("policyNumber", "Policy Number", true),
                        num("sumAssured", "Sum Assured", true),
                        num("premiumAmount", "Premium Amount", true),
                        select("premiumFrequency", "Premium Frequency", true, "MONTHLY", "QUARTERLY", "ANNUAL"),
                        date("policyStartDate", "Policy Start", true),
                        date("policyEndDate", "Policy End", true),
                        date("nextPremiumDueDate", "Next Premium Due", true),
                        check("is80CDeductible", "80C deductible (Old Regime)")),
                List.of("insurerName", "policyNumber", "sumAssured", "premiumAmount", "nextPremiumDueDate", "daysToPremium"),
                null, null,
                Map.of("daysToPremium", (r, u) -> daysTo(r.getNextPremiumDueDate())),
                true));

        add(new ModuleDef<>("health-insurance", "Health Insurance", "08 §19", db.healthInsurance, HealthInsurance.class,
                Bucket.NONE,
                List.of(text("insurerName", "Insurer", true),
                        text("policyNumber", "Policy Number", true),
                        num("sumInsured", "Sum Insured", true),
                        num("premiumAmount", "Premium Amount", true),
                        select("premiumFrequency", "Premium Frequency", true, "MONTHLY", "QUARTERLY", "ANNUAL"),
                        select("policyType", "Policy Type", true, "INDIVIDUAL", "FAMILY_FLOATER"),
                        text("membersCovered", "Members Covered", false, "Comma-separated names, for floater policies")
                                .withVisibleIf("policyType=FAMILY_FLOATER"),
                        date("policyStartDate", "Policy Start", true),
                        date("policyEndDate", "Policy End", true),
                        date("nextRenewalDate", "Next Renewal", true),
                        check("is80DDeductible", "80D deductible (Old Regime)"),
                        num("topUpSumInsured", "Top-up Sum Insured", false),
                        num("topUpDeductible", "Top-up Deductible", false)),
                List.of("insurerName", "policyNumber", "sumInsured", "policyType", "nextRenewalDate", "daysToRenewal"),
                null, null,
                Map.of("daysToRenewal", (r, u) -> daysTo(r.getNextRenewalDate())),
                true));

        add(new ModuleDef<>("lic-policies", "LIC / Endowment / ULIP", "08 §20", db.licPolicies, LicPolicy.class,
                Bucket.INVESTMENTS,   // at surrender value only
                List.of(text("insurerName", "Insurer", true),
                        text("policyName", "Policy Name", true),
                        text("policyNumber", "Policy Number", true),
                        select("policyType", "Policy Type", true, "ENDOWMENT", "MONEY_BACK", "WHOLE_LIFE", "ULIP"),
                        num("sumAssured", "Sum Assured", true),
                        num("premiumAmount", "Premium Amount", true),
                        select("premiumFrequency", "Premium Frequency", true, "MONTHLY", "QUARTERLY", "ANNUAL"),
                        date("policyStartDate", "Policy Start", true),
                        date("maturityDate", "Maturity Date", false),
                        date("nextPremiumDueDate", "Next Premium Due", true),
                        num("surrenderValue", "Surrender Value", false, "The only figure counted toward net worth"),
                        num("bonusAccrued", "Bonus Accrued", false),
                        num("expectedMaturityValue", "Expected Maturity Value", false),
                        num("ulipFundValue", "ULIP Fund Value", false).withVisibleIf("policyType=ULIP"),
                        num("ulipEquityAllocationPercent", "ULIP Equity %", false).withVisibleIf("policyType=ULIP"),
                        num("ulipDebtAllocationPercent", "ULIP Debt %", false).withVisibleIf("policyType=ULIP"),
                        check("is80CDeductible", "80C deductible (Old Regime)")),
                List.of("policyName", "policyType", "sumAssured", "premiumAmount", "surrenderValue", "maturityDate"),
                (r, u) -> nz(r.getSurrenderValue()),
                (r, u) -> "ULIP".equals(r.getPolicyType())
                        ? splitEquityDebt(null, r.getUlipEquityAllocationPercent())
                        : Map.of(AllocationClass.DEBT, BigDecimal.ONE),
                Map.of(),
                true));

        // ── Section 09 — Liabilities ────────────────────────────────────────
        add(new ModuleDef<>("loans", "Loans & Credit Cards", "09 §21", db.loans, Loan.class,
                Bucket.LIABILITY,
                List.of(select("loanType", "Loan Type", true, "HOME_LOAN", "PERSONAL_LOAN", "CAR_LOAN",
                                "EDUCATION_LOAN", "CREDIT_CARD", "OTHER"),
                        text("lenderName", "Lender / Issuer", true),
                        text("loanAccountNumber", "Loan / Card Number", false),
                        date("startDate", "Start / Issuance Date", true),
                        num("principalAmount", "Principal Amount", false)
                                .withVisibleIf("loanType=HOME_LOAN|PERSONAL_LOAN|CAR_LOAN|EDUCATION_LOAN|OTHER"),
                        num("outstandingBalance", "Outstanding Balance", true,
                                "For credit cards: the current statement outstanding"),
                        num("interestRate", "Interest Rate %", false),
                        num("emiAmount", "EMI Amount", false)
                                .withVisibleIf("loanType=HOME_LOAN|PERSONAL_LOAN|CAR_LOAN|EDUCATION_LOAN|OTHER"),
                        num("tenureMonths", "Tenure (months)", false)
                                .withVisibleIf("loanType=HOME_LOAN|PERSONAL_LOAN|CAR_LOAN|EDUCATION_LOAN|OTHER"),
                        date("expectedClosureDate", "Expected Closure", false)
                                .withVisibleIf("loanType=HOME_LOAN|PERSONAL_LOAN|CAR_LOAN|EDUCATION_LOAN|OTHER"),
                        num("creditLimit", "Credit Limit", false).withVisibleIf("loanType=CREDIT_CARD"),
                        num("minimumAmountDue", "Minimum Amount Due", false).withVisibleIf("loanType=CREDIT_CARD"),
                        date("paymentDueDate", "Payment Due Date", false).withVisibleIf("loanType=CREDIT_CARD")),
                List.of("loanType", "lenderName", "outstandingBalance", "interestRate", "emiAmount", "monthsRemaining", "status"),
                (r, u) -> nz(r.getOutstandingBalance()),   // subtracted (the only subtracting module)
                null,                                       // liabilities never appear in allocation (Section 12)
                Map.of("status", (r, u) -> nz(r.getOutstandingBalance()).signum() == 0 ? "CLOSED" : "ACTIVE",
                        "monthsRemaining", (r, u) -> loanMonthsRemaining(r)),
                true));

        // ── Section 10 — Real Estate & Physical Assets ──────────────────────
        add(new ModuleDef<>("real-estate", "Real Estate", "10 §18", db.realEstate, RealEstate.class,
                Bucket.INVESTMENTS,
                List.of(text("propertyName", "Property Name", true),
                        select("propertyType", "Property Type", true, "RESIDENTIAL_APARTMENT", "RESIDENTIAL_HOUSE",
                                "COMMERCIAL_OFFICE", "COMMERCIAL_SHOP", "LAND", "PLOT", "OTHER"),
                        text("address", "Address", true),
                        text("city", "City", true),
                        text("state", "State", true),
                        num("purchaseValue", "Purchase Value", true),
                        date("purchaseDate", "Purchase Date", true),
                        num("currentEstimatedValue", "Current Estimated Value", true,
                                "Update periodically from a property portal or professional valuation"),
                        date("lastRevaluedDate", "Last Revalued", true),
                        num("rentalIncomePerMonth", "Monthly Rental Income", false,
                                "For reference only — add to Other Income for income tracking"),
                        text("documentRegistrationNumber", "Registration Number", false,
                                "Visible to family members with Contacts Only access"),
                        text("registrarOfficeAddress", "Registrar Office Address", false),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("propertyName", "propertyType", "city", "purchaseValue", "currentEstimatedValue", "rentalIncomePerMonth"),
                (r, u) -> convert(nz(r.getCurrentEstimatedValue()), r.getCurrency(), u),
                singleClass(AllocationClass.REAL_ESTATE),
                Map.of(),
                true));

        add(new ModuleDef<>("gold", "Gold", "10 §19", db.goldHoldings, GoldHolding.class,
                Bucket.INVESTMENTS,
                List.of(select("goldType", "Gold Type", true, "PHYSICAL_JEWELLERY", "PHYSICAL_COINS_BARS",
                                "DIGITAL_GOLD", "SGB", "GOLD_ETF", "GOLD_MUTUAL_FUND"),
                        text("label", "Label", true, "e.g. 'Wedding bangles' — visible at Contacts Only"),
                        select("purity", "Purity", false, "24K", "22K", "18K")
                                .withVisibleIf("goldType=PHYSICAL_JEWELLERY|PHYSICAL_COINS_BARS"),
                        num("weightGrams", "Weight (grams)", false)
                                .withVisibleIf("goldType=PHYSICAL_JEWELLERY|PHYSICAL_COINS_BARS"),
                        num("units", "Units", false)
                                .withVisibleIf("goldType=DIGITAL_GOLD|SGB|GOLD_ETF|GOLD_MUTUAL_FUND"),
                        num("purchasePricePerGramOrUnit", "Purchase Price (per gram/unit)", true),
                        date("purchaseDate", "Purchase Date", true),
                        num("currentNavPerUnit", "Current NAV / Price per Unit", false)
                                .withVisibleIf("goldType=DIGITAL_GOLD|SGB|GOLD_ETF|GOLD_MUTUAL_FUND"),
                        date("navAsOfDate", "NAV As Of", false)
                                .withVisibleIf("goldType=DIGITAL_GOLD|SGB|GOLD_ETF|GOLD_MUTUAL_FUND"),
                        num("sgbCouponRate", "SGB Coupon Rate %", false).withVisibleIf("goldType=SGB"),
                        date("sgbMaturityDate", "SGB Maturity", false).withVisibleIf("goldType=SGB"),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("label", "goldType", "purity", "weightGrams", "units", "currentValue"),
                (r, u) -> convert(goldValue(r, u), r.getCurrency(), u),
                singleClass(AllocationClass.GOLD),
                Map.of("currentValue", (r, u) -> goldValue(r, u)),
                true));

        add(new ModuleDef<>("physical-assets", "Other Physical Assets", "10 §20", db.physicalAssets, PhysicalAsset.class,
                Bucket.INVESTMENTS,
                List.of(text("assetName", "Asset Name", true),
                        select("assetType", "Asset Type", true, "VEHICLE", "ART_COLLECTIBLE", "ELECTRONICS",
                                "JEWELLERY_OTHER", "OTHER"),
                        num("purchaseValue", "Purchase Value", true),
                        date("purchaseDate", "Purchase Date", true),
                        num("currentEstimatedValue", "Current Estimated Value", true),
                        date("lastRevaluedDate", "Last Revalued", true),
                        text("registrationNumber", "Registration Number", false,
                                "Vehicle registration, provenance reference, etc. — visible at Contacts Only"),
                        text("insurerName", "Insurer", false),
                        text("insurancePolicyNumber", "Insurance Policy Number", false),
                        select("currency", "Currency", true, "INR", "USD")),
                List.of("assetName", "assetType", "purchaseValue", "currentEstimatedValue"),
                (r, u) -> convert(nz(r.getCurrentEstimatedValue()), r.getCurrency(), u),
                singleClass(AllocationClass.ALTERNATIVE),
                Map.of(),
                true));

        // ── Sections 11 / 11A — Goals, Other Income (Salary is custom) ──────
        add(new ModuleDef<>("goals", "Financial Goals", "11 §22", db.financialGoals, FinancialGoal.class,
                Bucket.NONE,
                List.of(text("goalName", "Goal Name", true),
                        select("goalType", "Goal Type", true, "RETIREMENT", "HOUSE", "EDUCATION", "WEDDING",
                                "TRAVEL", "EMERGENCY_FUND", "CUSTOM"),
                        num("targetAmountToday", "Target Amount (today's money)", true),
                        date("targetDate", "Target Date", true),
                        num("expectedAnnualReturnPercent", "Expected Annual Return %", true),
                        num("expectedAnnualInflationPercent", "Expected Annual Inflation %", true),
                        num("sipStepUpPercent", "Annual SIP Step-Up %", false,
                                "Optional — increases the required SIP each year instead of a flat monthly amount"),
                        check("recurring", "Recurring goal (e.g. an annual holiday)"),
                        num("recurrenceIntervalYears", "Repeats Every (years)", false).withVisibleIf("recurring=true")),
                List.of("goalName", "goalType", "targetAmountToday", "targetDate", "nominalSip", "inflationAdjustedSip"),
                null, null,
                Map.of("nominalSip", (r, u) -> goalSips(r)[0],
                        "inflationAdjustedSip", (r, u) -> goalSips(r)[1]),
                true));

        add(new ModuleDef<>("other-income", "Other Income", "11 §23", db.otherIncome, OtherIncome.class,
                Bucket.NONE,
                List.of(select("sourceType", "Source", true, "FREELANCE", "RENTAL", "INTEREST", "DIVIDEND",
                                "BONUS", "CAPITAL_GAINS", "OTHER"),
                        text("description", "Description", true),
                        num("amount", "Amount", true),
                        select("frequency", "Frequency", true, "ONE_TIME", "MONTHLY", "QUARTERLY", "ANNUAL"),
                        date("dateReceived", "Date Received", true),
                        check("isTaxable", "Taxable")),
                List.of("sourceType", "description", "amount", "frequency", "dateReceived"),
                null, null,
                Map.of(),
                true));

        add(new ModuleDef<>("salary", "Salary Profiles", "11A §24", db.salaryProfiles, SalaryProfile.class,
                Bucket.NONE,
                List.of(),   // custom dynamic-row form
                List.of("employerName", "financialYear", "regime", "grossMonthly"),
                null, null,
                Map.of("grossMonthly", (r, u) -> r.grossMonthly()),
                false));
    }

    private void add(ModuleDef<?> def) {
        modules.add(def);
        byPath.put(def.path, def);
    }

    public List<ModuleDef<?>> all() { return modules; }
    public ModuleDef<?> byPath(String path) { return byPath.get(path); }

    // ── shared helpers ────────────────────────────────────────────────────────

    /**
     * ALLOCATION CONTRACT: allocation functions return FRACTIONS of the record's value
     * (summing to 1); the aggregation service multiplies by the module's value function.
     */
    private static <T extends OwnedRecord> BiFunction<T, User, Map<AllocationClass, BigDecimal>> singleClass(AllocationClass cls) {
        return (r, u) -> Map.of(cls, BigDecimal.ONE);
    }

    private static Map<AllocationClass, BigDecimal> splitEquityDebt(BigDecimal ignoredValue, BigDecimal equityPercent) {
        BigDecimal eq = (equityPercent == null ? BigDecimal.ZERO : equityPercent)
                .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        if (eq.compareTo(BigDecimal.ONE) > 0) eq = BigDecimal.ONE;
        return Map.of(AllocationClass.EQUITY, eq, AllocationClass.DEBT, BigDecimal.ONE.subtract(eq));
    }

    private static Map<AllocationClass, BigDecimal> mfAllocation(MutualFund mf, User u) {
        String c = mf.getCategory() == null ? "OTHER" : mf.getCategory();
        if (c.startsWith("DEBT_")) return Map.of(AllocationClass.DEBT, BigDecimal.ONE);
        if (c.startsWith("HYBRID_")) return splitEquityDebt(null, mf.getEquityAllocationPercent());
        if ((c.equals("FUND_OF_FUNDS") || c.equals("OTHER")) && mf.getEquityAllocationPercent() != null)
            return splitEquityDebt(null, mf.getEquityAllocationPercent());
        return Map.of(AllocationClass.EQUITY, BigDecimal.ONE);
    }

    private static String mask(String number) {
        if (number == null || number.length() < 4) return "••••";
        return "••••" + number.substring(number.length() - 4);
    }

    private static Long daysTo(LocalDate date) {
        return date == null ? null : ChronoUnit.DAYS.between(LocalDate.now(), date);
    }

    private static Long loanMonthsRemaining(Loan r) {
        if (r.getTenureMonths() == null || r.getStartDate() == null || "CREDIT_CARD".equals(r.getLoanType())) return null;
        long elapsed = ChronoUnit.MONTHS.between(r.getStartDate(), LocalDate.now());
        return Math.max(0, r.getTenureMonths() - elapsed);
    }
}
