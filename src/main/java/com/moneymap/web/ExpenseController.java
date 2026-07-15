package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.model.expense.ExpenseEntry;
import com.moneymap.repository.Db;
import com.moneymap.service.ExpenseService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Expense tracking API (standalone module, isolated from Assets/Liabilities). JSON-only —
 * no Thymeleaf views here; the UI for this lands with the upcoming redesign.
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final Db db;
    private final ExpenseService expenseService;

    public ExpenseController(Db db, ExpenseService expenseService) {
        this.db = db;
        this.expenseService = expenseService;
    }

    private User user(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    private ExpenseEntry owned(String id, User user) {
        return db.expenseEntries.findById(id)
                .filter(e -> user.getId().equals(e.getOwnerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public List<ExpenseEntry> list(HttpServletRequest request) {
        return expenseService.findForOwner(user(request).getId());
    }

    @GetMapping("/{id}")
    public ExpenseEntry get(@PathVariable String id, HttpServletRequest request) {
        return owned(id, user(request));
    }

    @GetMapping("/categories")
    public List<String> presetCategories() {
        return ExpenseEntry.PRESET_CATEGORIES;
    }

    @PostMapping
    public ExpenseEntry create(@RequestParam BigDecimal amount,
                               @RequestParam LocalDate date,
                               @RequestParam String category,
                               @RequestParam(required = false) String note,
                               @RequestParam(defaultValue = "EXPENSE") String direction,
                               @RequestParam(required = false) String linkedSalaryProfileId,
                               @RequestParam(required = false) String linkedAccountId,
                               @RequestParam(defaultValue = "INR") String currency,
                               @RequestParam(required = false) String familyMemberTag,
                               HttpServletRequest request) {
        User user = user(request);
        ExpenseEntry entry = new ExpenseEntry();
        entry.setOwnerId(user.getId());
        entry.setAmount(amount);
        entry.setDate(date);
        entry.setCategory(category);
        entry.setNote(note);
        entry.setDirection(direction);
        entry.setLinkedSalaryProfileId(linkedSalaryProfileId);
        entry.setLinkedAccountId(linkedAccountId);
        entry.setCurrency(currency);
        if (familyMemberTag != null && !familyMemberTag.isBlank()) entry.setFamilyMemberTag(familyMemberTag);
        return db.expenseEntries.save(entry);
    }

    @PutMapping("/{id}")
    public ExpenseEntry update(@PathVariable String id,
                               @RequestParam BigDecimal amount,
                               @RequestParam LocalDate date,
                               @RequestParam String category,
                               @RequestParam(required = false) String note,
                               @RequestParam(defaultValue = "EXPENSE") String direction,
                               @RequestParam(required = false) String linkedSalaryProfileId,
                               @RequestParam(required = false) String linkedAccountId,
                               @RequestParam(defaultValue = "INR") String currency,
                               HttpServletRequest request) {
        ExpenseEntry entry = owned(id, user(request));
        entry.setAmount(amount);
        entry.setDate(date);
        entry.setCategory(category);
        entry.setNote(note);
        entry.setDirection(direction);
        entry.setLinkedSalaryProfileId(linkedSalaryProfileId);
        entry.setLinkedAccountId(linkedAccountId);
        entry.setCurrency(currency);
        return db.expenseEntries.save(entry);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, HttpServletRequest request) {
        owned(id, user(request));
        db.expenseEntries.deleteById(id);
    }

    @GetMapping("/totals/by-day")
    public Map<LocalDate, BigDecimal> totalsByDay(HttpServletRequest request) {
        return expenseService.totalsByDay(user(request).getId());
    }

    @GetMapping("/totals/by-month")
    public Map<String, BigDecimal> totalsByMonth(HttpServletRequest request) {
        Map<java.time.YearMonth, BigDecimal> raw = expenseService.totalsByMonth(user(request).getId());
        return raw.entrySet().stream().collect(java.util.stream.Collectors.toMap(
                en -> en.getKey().toString(), Map.Entry::getValue,
                (a, b) -> a, java.util.LinkedHashMap::new));
    }

    @GetMapping("/totals/by-year")
    public Map<Integer, BigDecimal> totalsByYear(HttpServletRequest request) {
        return expenseService.totalsByYear(user(request).getId());
    }

    @GetMapping("/totals/by-category")
    public Map<String, BigDecimal> totalsByCategory(HttpServletRequest request) {
        return expenseService.totalsByCategory(user(request).getId());
    }

    @GetMapping("/totals/by-family-member")
    public Map<String, BigDecimal> totalsByFamilyMember(HttpServletRequest request) {
        return expenseService.totalsByFamilyMember(user(request).getId());
    }
}
