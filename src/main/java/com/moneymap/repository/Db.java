package com.moneymap.repository;

import com.moneymap.model.*;
import com.moneymap.model.asset.*;
import com.moneymap.model.expense.*;
import com.moneymap.repository.json.JsonEntityRepository;
import org.springframework.stereotype.Component;

/**
 * Central registry of typed repositories, one per collection (Section 17). Each field is
 * the EntityRepository interface backed by the JSON implementation — swapping storage
 * later means changing only this wiring (Section 00 §5.1's interface-first promise).
 */
@Component
public class Db {

    // Family & platform
    public final EntityRepository<FamilyGroup> familyGroups;
    public final EntityRepository<FamilyInvitation> familyInvitations;
    public final EntityRepository<FamilyPermission> familyPermissions;
    public final EntityRepository<EmergencyAccessRequest> emergencyRequests;
    public final EntityRepository<Notification> notifications;
    public final EntityRepository<TaxSlabSet> taxSlabSets;
    public final EntityRepository<NetWorthSnapshot> netWorthSnapshots;
    public final EntityRepository<AllocationTarget> allocationTargets;
    public final EntityRepository<FundMaster> fundMaster;
    public final EntityRepository<FinancialHealthInputs> financialHealthInputs;
    public final EntityRepository<MutualFundTransaction> mutualFundTransactions;

    // Cash & banking (Section 05)
    public final EntityRepository<SavingsAccount> savingsAccounts;
    public final EntityRepository<CashInHand> cashInHand;
    public final EntityRepository<FixedDeposit> fixedDeposits;
    public final EntityRepository<RecurringDeposit> recurringDeposits;
    public final EntityRepository<FlexiRd> flexiRds;

    // Retirement (Section 07)
    public final EntityRepository<PfAccount> pfAccounts;
    public final EntityRepository<PfEmployerRecord> pfEmployerRecords;
    public final EntityRepository<PpfAccount> ppfAccounts;
    public final EntityRepository<NpsAccount> npsAccounts;
    public final EntityRepository<Gratuity> gratuities;
    public final EntityRepository<GovernmentScheme> governmentSchemes;

    // Investments (Section 06)
    public final EntityRepository<MutualFund> mutualFunds;
    public final EntityRepository<EquityHolding> equityHoldings;
    public final EntityRepository<Bond> bonds;
    public final EntityRepository<CryptoHolding> cryptoHoldings;
    public final EntityRepository<Esop> esops;

    // Insurance (Section 08)
    public final EntityRepository<TermInsurance> termInsurance;
    public final EntityRepository<HealthInsurance> healthInsurance;
    public final EntityRepository<LicPolicy> licPolicies;

    // Liabilities (Section 09)
    public final EntityRepository<Loan> loans;

    // Real estate & physical (Section 10)
    public final EntityRepository<RealEstate> realEstate;
    public final EntityRepository<GoldHolding> goldHoldings;
    public final EntityRepository<PhysicalAsset> physicalAssets;

    // Goals, income, salary (Sections 11/11A)
    public final EntityRepository<FinancialGoal> financialGoals;
    public final EntityRepository<OtherIncome> otherIncome;
    public final EntityRepository<SalaryProfile> salaryProfiles;

    // Expense tracking, recurring transactions, documents (standalone modules)
    public final EntityRepository<ExpenseEntry> expenseEntries;
    public final EntityRepository<RecurringRule> recurringRules;
    public final EntityRepository<Document> documents;

    public Db(JsonCollectionStore store) {
        familyGroups = new JsonEntityRepository<>(store, "family_groups", FamilyGroup.class);
        familyInvitations = new JsonEntityRepository<>(store, "family_invitations", FamilyInvitation.class);
        familyPermissions = new JsonEntityRepository<>(store, "family_permissions", FamilyPermission.class);
        emergencyRequests = new JsonEntityRepository<>(store, "emergency_access_requests", EmergencyAccessRequest.class);
        notifications = new JsonEntityRepository<>(store, "notifications", Notification.class);
        taxSlabSets = new JsonEntityRepository<>(store, "tax_slab_sets", TaxSlabSet.class);
        netWorthSnapshots = new JsonEntityRepository<>(store, "net_worth_snapshots", NetWorthSnapshot.class);
        allocationTargets = new JsonEntityRepository<>(store, "allocation_targets", AllocationTarget.class);
        fundMaster = new JsonEntityRepository<>(store, "fund_master", FundMaster.class);
        financialHealthInputs = new JsonEntityRepository<>(store, "financial_health_inputs", FinancialHealthInputs.class);
        mutualFundTransactions = new JsonEntityRepository<>(store, "mutual_fund_transactions", MutualFundTransaction.class);
        savingsAccounts = new JsonEntityRepository<>(store, "savings_accounts", SavingsAccount.class);
        cashInHand = new JsonEntityRepository<>(store, "cash_in_hand", CashInHand.class);
        fixedDeposits = new JsonEntityRepository<>(store, "fixed_deposits", FixedDeposit.class);
        recurringDeposits = new JsonEntityRepository<>(store, "recurring_deposits", RecurringDeposit.class);
        flexiRds = new JsonEntityRepository<>(store, "flexi_rds", FlexiRd.class);
        pfAccounts = new JsonEntityRepository<>(store, "pf_accounts", PfAccount.class);
        pfEmployerRecords = new JsonEntityRepository<>(store, "pf_employer_records", PfEmployerRecord.class);
        ppfAccounts = new JsonEntityRepository<>(store, "ppf_accounts", PpfAccount.class);
        npsAccounts = new JsonEntityRepository<>(store, "nps_accounts", NpsAccount.class);
        gratuities = new JsonEntityRepository<>(store, "gratuities", Gratuity.class);
        governmentSchemes = new JsonEntityRepository<>(store, "government_schemes", GovernmentScheme.class);
        mutualFunds = new JsonEntityRepository<>(store, "mutual_funds", MutualFund.class);
        equityHoldings = new JsonEntityRepository<>(store, "equity_holdings", EquityHolding.class);
        bonds = new JsonEntityRepository<>(store, "bonds", Bond.class);
        cryptoHoldings = new JsonEntityRepository<>(store, "crypto_holdings", CryptoHolding.class);
        esops = new JsonEntityRepository<>(store, "esops", Esop.class);
        termInsurance = new JsonEntityRepository<>(store, "term_insurance", TermInsurance.class);
        healthInsurance = new JsonEntityRepository<>(store, "health_insurance", HealthInsurance.class);
        licPolicies = new JsonEntityRepository<>(store, "lic_endowment_ulip", LicPolicy.class);
        loans = new JsonEntityRepository<>(store, "loans", Loan.class);
        realEstate = new JsonEntityRepository<>(store, "real_estate", RealEstate.class);
        goldHoldings = new JsonEntityRepository<>(store, "gold_holdings", GoldHolding.class);
        physicalAssets = new JsonEntityRepository<>(store, "physical_assets", PhysicalAsset.class);
        financialGoals = new JsonEntityRepository<>(store, "financial_goals", FinancialGoal.class);
        otherIncome = new JsonEntityRepository<>(store, "other_income", OtherIncome.class);
        salaryProfiles = new JsonEntityRepository<>(store, "salary_profiles", SalaryProfile.class);
        expenseEntries = new JsonEntityRepository<>(store, "expense_entries", ExpenseEntry.class);
        recurringRules = new JsonEntityRepository<>(store, "recurring_rules", RecurringRule.class);
        documents = new JsonEntityRepository<>(store, "documents", Document.class);
    }
}
