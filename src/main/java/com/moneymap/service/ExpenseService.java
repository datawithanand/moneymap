package com.moneymap.service;

import com.moneymap.model.expense.ExpenseEntry;
import com.moneymap.repository.Db;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** CRUD helpers and aggregation for the standalone expense-tracking module. */
@Service
public class ExpenseService {

    private final Db db;

    public ExpenseService(Db db) {
        this.db = db;
    }

    public List<ExpenseEntry> findForOwner(String ownerId) {
        return db.expenseEntries.findWhere(e -> ownerId.equals(e.getOwnerId())).stream()
                .sorted(Comparator.comparing(ExpenseEntry::getDate).reversed())
                .toList();
    }

    private BigDecimal signed(ExpenseEntry e) {
        BigDecimal amount = e.getAmount() == null ? BigDecimal.ZERO : e.getAmount();
        return "INCOME".equals(e.getDirection()) ? amount : amount.negate();
    }

    /** Net (income - expense) grouped by calendar day, most recent first. */
    public Map<LocalDate, BigDecimal> totalsByDay(String ownerId) {
        Map<LocalDate, BigDecimal> out = new LinkedHashMap<>();
        findForOwner(ownerId).forEach(e -> {
            if (e.getDate() == null) return;
            out.merge(e.getDate(), signed(e), BigDecimal::add);
        });
        return out;
    }

    /** Net grouped by calendar month. */
    public Map<YearMonth, BigDecimal> totalsByMonth(String ownerId) {
        Map<YearMonth, BigDecimal> out = new LinkedHashMap<>();
        findForOwner(ownerId).forEach(e -> {
            if (e.getDate() == null) return;
            out.merge(YearMonth.from(e.getDate()), signed(e), BigDecimal::add);
        });
        return out;
    }

    /** Net grouped by calendar year. */
    public Map<Integer, BigDecimal> totalsByYear(String ownerId) {
        Map<Integer, BigDecimal> out = new LinkedHashMap<>();
        findForOwner(ownerId).forEach(e -> {
            if (e.getDate() == null) return;
            out.merge(e.getDate().getYear(), signed(e), BigDecimal::add);
        });
        return out;
    }

    /** Absolute expense total (outflow only) grouped by category. */
    public Map<String, BigDecimal> totalsByCategory(String ownerId) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        findForOwner(ownerId).stream()
                .filter(e -> "EXPENSE".equals(e.getDirection()))
                .forEach(e -> out.merge(
                        e.getCategory() == null ? "Other" : e.getCategory(),
                        e.getAmount() == null ? BigDecimal.ZERO : e.getAmount(),
                        BigDecimal::add));
        return out;
    }

    /** Net total grouped by family member tag. */
    public Map<String, BigDecimal> totalsByFamilyMember(String ownerId) {
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        findForOwner(ownerId).forEach(e -> out.merge(
                e.getFamilyMemberTag() == null ? "Self" : e.getFamilyMemberTag(),
                signed(e), BigDecimal::add));
        return out;
    }
}
