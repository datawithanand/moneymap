package com.moneymap.web;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Instant formatting for Thymeleaf views, rendered in the instance time zone
 * (TZ env var, default Asia/Kolkata — Section 15 §5). Exposed to all templates as "fmt".
 * Per-user dateFormat preference (Section 01 §5.2) is applied to financial-record dates
 * in the module increments; these helpers cover platform screens (admin tables, audit log).
 */
@Component
public class DateFmt {

    private final ZoneId zone = ZoneId.systemDefault();

    public String date(Instant instant) {
        return instant == null ? "—" : DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(zone).format(instant);
    }

    public String dateTime(Instant instant) {
        return instant == null ? "—" : DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm").withZone(zone).format(instant);
    }

    public String full(Instant instant) {
        return instant == null ? "—" : DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss").withZone(zone).format(instant);
    }

    /** Formats any cell value: money with Indian/International grouping per the user's preference (§5.2). */
    public String cell(Object value, com.moneymap.model.User user) {
        if (value == null) return "—";
        if (value instanceof java.math.BigDecimal bd) return money(bd, user);
        return String.valueOf(value);
    }

    public String money(java.math.BigDecimal amount, com.moneymap.model.User user) {
        if (amount == null) return "—";
        boolean indian = user == null || user.getNumberFormat() == com.moneymap.model.User.NumberFormat.INDIAN;
        String symbol = user != null && user.getCurrencyPreference() == com.moneymap.model.User.CurrencyPreference.USD ? "$" : "₹";
        java.math.BigDecimal rounded = amount.setScale(2, java.math.RoundingMode.HALF_UP);
        String[] parts = rounded.abs().toPlainString().split("\\.");
        String grouped = indian ? indianGroup(parts[0]) : internationalGroup(parts[0]);
        return (amount.signum() < 0 ? "−" : "") + symbol + grouped + "." + parts[1];
    }

    private String indianGroup(String digits) {
        if (digits.length() <= 3) return digits;
        String last3 = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        StringBuilder sb = new StringBuilder();
        while (rest.length() > 2) {
            sb.insert(0, "," + rest.substring(rest.length() - 2));
            rest = rest.substring(0, rest.length() - 2);
        }
        return rest + sb + "," + last3;
    }

    private String internationalGroup(String digits) {
        StringBuilder sb = new StringBuilder(digits);
        for (int i = digits.length() - 3; i > 0; i -= 3) sb.insert(i, ',');
        return sb.toString();
    }
}
