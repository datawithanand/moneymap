package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.model.expense.RecurringRule;
import com.moneymap.repository.Db;
import com.moneymap.service.RecurringTransactionScheduler;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Recurring transaction rules API (standalone module). JSON-only, no views yet. */
@RestController
@RequestMapping("/api/recurring-rules")
public class RecurringRuleController {

    private final Db db;
    private final RecurringTransactionScheduler scheduler;

    public RecurringRuleController(Db db, RecurringTransactionScheduler scheduler) {
        this.db = db;
        this.scheduler = scheduler;
    }

    private User user(HttpServletRequest request) {
        return (User) request.getAttribute("currentUser");
    }

    private RecurringRule owned(String id, User user) {
        return db.recurringRules.findById(id)
                .filter(r -> user.getId().equals(r.getOwnerId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping
    public List<RecurringRule> list(HttpServletRequest request) {
        String uid = user(request).getId();
        return db.recurringRules.findWhere(r -> uid.equals(r.getOwnerId())).stream()
                .sorted(Comparator.comparing(RecurringRule::getNextDueDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @GetMapping("/{id}")
    public RecurringRule get(@PathVariable String id, HttpServletRequest request) {
        return owned(id, user(request));
    }

    @PostMapping
    public RecurringRule create(@RequestParam BigDecimal amount,
                                @RequestParam String category,
                                @RequestParam(defaultValue = "EXPENSE") String direction,
                                @RequestParam String frequency,
                                @RequestParam LocalDate nextDueDate,
                                @RequestParam(defaultValue = "true") boolean active,
                                @RequestParam(required = false) String note,
                                @RequestParam(defaultValue = "INR") String currency,
                                @RequestParam(required = false) String familyMemberTag,
                                HttpServletRequest request) {
        User user = user(request);
        RecurringRule rule = new RecurringRule();
        rule.setOwnerId(user.getId());
        rule.setAmount(amount);
        rule.setCategory(category);
        rule.setDirection(direction);
        rule.setFrequency(frequency);
        rule.setNextDueDate(nextDueDate);
        rule.setActive(active);
        rule.setNote(note);
        rule.setCurrency(currency);
        if (familyMemberTag != null && !familyMemberTag.isBlank()) rule.setFamilyMemberTag(familyMemberTag);
        return db.recurringRules.save(rule);
    }

    @PutMapping("/{id}")
    public RecurringRule update(@PathVariable String id,
                                @RequestParam BigDecimal amount,
                                @RequestParam String category,
                                @RequestParam(defaultValue = "EXPENSE") String direction,
                                @RequestParam String frequency,
                                @RequestParam LocalDate nextDueDate,
                                @RequestParam(defaultValue = "true") boolean active,
                                @RequestParam(required = false) String note,
                                @RequestParam(defaultValue = "INR") String currency,
                                HttpServletRequest request) {
        RecurringRule rule = owned(id, user(request));
        rule.setAmount(amount);
        rule.setCategory(category);
        rule.setDirection(direction);
        rule.setFrequency(frequency);
        rule.setNextDueDate(nextDueDate);
        rule.setActive(active);
        rule.setNote(note);
        rule.setCurrency(currency);
        return db.recurringRules.save(rule);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id, HttpServletRequest request) {
        owned(id, user(request));
        db.recurringRules.deleteById(id);
    }

    /** Manually trigger the due-entry generation job (normally runs daily at 00:05). */
    @PostMapping("/run-due-now")
    public Map<String, Integer> runDueNow() {
        return Map.of("generated", scheduler.runOnce());
    }
}
