package com.moneymap.service;

import com.moneymap.model.expense.ExpenseEntry;
import com.moneymap.model.expense.RecurringRule;
import com.moneymap.repository.Db;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * Daily job: for every active RecurringRule whose nextDueDate has arrived, generates the
 * corresponding ExpenseEntry and advances nextDueDate to the following occurrence. Runs once
 * a day just after midnight; also safe to have missed a run (catches up anything overdue).
 */
@Component
public class RecurringTransactionScheduler {

    private final Db db;

    public RecurringTransactionScheduler(Db db) {
        this.db = db;
    }

    @Scheduled(cron = "0 5 0 * * *")
    public void generateDueEntries() {
        runOnce();
    }

    /** Also callable directly (e.g. on demand from an admin action or a test). */
    public int runOnce() {
        LocalDate today = LocalDate.now();
        int generated = 0;
        for (RecurringRule rule : db.recurringRules.findWhere(RecurringRule::isActive)) {
            boolean advanced = false;
            while (rule.getNextDueDate() != null && !rule.getNextDueDate().isAfter(today)) {
                ExpenseEntry entry = new ExpenseEntry();
                entry.setOwnerId(rule.getOwnerId());
                entry.setFamilyMemberTag(rule.getFamilyMemberTag());
                entry.setAmount(rule.getAmount());
                entry.setDate(rule.getNextDueDate());
                entry.setCategory(rule.getCategory());
                entry.setDirection(rule.getDirection());
                entry.setCurrency(rule.getCurrency());
                entry.setNote(rule.getNote());
                entry.setRecurringRuleId(rule.getId());
                db.expenseEntries.save(entry);
                generated++;
                rule.setNextDueDate(rule.advance(rule.getNextDueDate()));
                advanced = true;
            }
            if (advanced) db.recurringRules.save(rule);
        }
        return generated;
    }
}
