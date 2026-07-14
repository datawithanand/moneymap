package com.moneymap.service;

import com.moneymap.model.User;
import com.moneymap.model.asset.*;
import com.moneymap.repository.Db;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Consolidates every due-date already tracked across the asset modules (FD/RD/government
 * scheme maturity, insurance premium/renewal, credit-card payment due, active SIP dates) into
 * a single sorted reminder list. No new data is stored — every date already lives on its
 * owning record; this just aggregates and sorts what's already there (Main Overview widget +
 * dedicated Upcoming Reminders page).
 */
@Service
public class ReminderService {

    public record ReminderItem(String modulePath, String recordId, String description,
                                LocalDate dueDate, long daysUntil, String kind) {}

    private final Db db;

    public ReminderService(Db db) {
        this.db = db;
    }

    /** Everything due within the next `withinDays` days, plus anything already overdue, soonest first. */
    public List<ReminderItem> upcoming(User owner, int withinDays) {
        String uid = owner.getId();
        LocalDate today = LocalDate.now();
        List<ReminderItem> items = new ArrayList<>();

        for (FixedDeposit r : db.fixedDeposits.findWhere(x -> uid.equals(x.getOwnerId()))) {
            add(items, "fixed-deposits", r.getId(), r.getBankName() + " FD maturing", r.getMaturityDate(), today, "MATURITY");
        }
        for (RecurringDeposit r : db.recurringDeposits.findWhere(x -> uid.equals(x.getOwnerId()))) {
            LocalDate maturity = (r.getStartDate() != null && r.getTenureMonths() != null)
                    ? r.getStartDate().plusMonths(r.getTenureMonths()) : null;
            add(items, "recurring-deposits", r.getId(), r.getBankName() + " RD maturing", maturity, today, "MATURITY");
        }
        for (GovernmentScheme r : db.governmentSchemes.findWhere(x -> uid.equals(x.getOwnerId()))) {
            add(items, "government-schemes", r.getId(),
                    (r.getSchemeName() != null ? r.getSchemeName() : r.getSchemeType()) + " maturing",
                    r.getMaturityDate(), today, "MATURITY");
        }
        for (TermInsurance r : db.termInsurance.findWhere(x -> uid.equals(x.getOwnerId()))) {
            add(items, "term-insurance", r.getId(), r.getInsurerName() + " premium due",
                    r.getNextPremiumDueDate(), today, "PREMIUM_DUE");
        }
        for (HealthInsurance r : db.healthInsurance.findWhere(x -> uid.equals(x.getOwnerId()))) {
            add(items, "health-insurance", r.getId(), r.getInsurerName() + " renewal due",
                    r.getNextRenewalDate(), today, "RENEWAL_DUE");
        }
        for (LicPolicy r : db.licPolicies.findWhere(x -> uid.equals(x.getOwnerId()))) {
            add(items, "lic-policies", r.getId(), r.getInsurerName() + " premium due",
                    r.getNextPremiumDueDate(), today, "PREMIUM_DUE");
        }
        for (Loan r : db.loans.findWhere(x -> uid.equals(x.getOwnerId()))) {
            if ("CREDIT_CARD".equals(r.getLoanType())) {
                add(items, "loans", r.getId(), r.getLenderName() + " card payment due",
                        r.getPaymentDueDate(), today, "PAYMENT_DUE");
            }
        }
        for (MutualFund r : db.mutualFunds.findWhere(x -> uid.equals(x.getOwnerId()))) {
            if (r.getSipDate() != null && "ACTIVE".equals(r.getSipStatus())) {
                add(items, "mutual-funds", r.getId(), r.getFundName() + " SIP due",
                        nextOccurrence(r.getSipDate(), today), today, "SIP_DUE");
            }
        }

        return items.stream()
                .filter(i -> i.daysUntil() <= withinDays)
                .sorted(Comparator.comparingLong(ReminderItem::daysUntil))
                .toList();
    }

    private static void add(List<ReminderItem> items, String modulePath, String recordId, String description,
                            LocalDate dueDate, LocalDate today, String kind) {
        if (dueDate == null) return;
        items.add(new ReminderItem(modulePath, recordId, description, dueDate, ChronoUnit.DAYS.between(today, dueDate), kind));
    }

    /** Next calendar occurrence of a day-of-month, clamped to short months (e.g. 31 -> Feb 28/29). */
    private static LocalDate nextOccurrence(int dayOfMonth, LocalDate today) {
        LocalDate thisMonth = clampToMonth(today.withDayOfMonth(1), dayOfMonth);
        if (!thisMonth.isBefore(today)) return thisMonth;
        LocalDate nextMonth = today.plusMonths(1).withDayOfMonth(1);
        return clampToMonth(nextMonth, dayOfMonth);
    }

    private static LocalDate clampToMonth(LocalDate firstOfMonth, int dayOfMonth) {
        int lastDay = firstOfMonth.lengthOfMonth();
        return firstOfMonth.withDayOfMonth(Math.min(dayOfMonth, lastDay));
    }
}
