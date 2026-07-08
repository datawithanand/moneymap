package com.moneymap.calculator;

import java.math.BigDecimal;

/** Shared input-validation helpers for the calculator controllers (Section: Calculators). */
public final class CalculatorValidation {

    private CalculatorValidation() {}

    /** Thrown for any input/calculation error a calculator controller should show back to the user. */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) { super(message); }
    }

    public static String positive(BigDecimal v, String label) {
        return (v == null || v.signum() <= 0) ? label + " must be greater than zero." : null;
    }

    public static String nonNegative(BigDecimal v, String label) {
        return (v != null && v.signum() < 0) ? label + " cannot be negative." : null;
    }

    public static String percentRange(BigDecimal v, String label) {
        return (v != null && (v.signum() < 0 || v.compareTo(BigDecimal.valueOf(100)) > 0))
                ? label + " must be between 0 and 100." : null;
    }

    public static String range(int v, int min, int max, String label) {
        return (v < min || v > max) ? label + " must be between " + min + " and " + max + "." : null;
    }

    /** Returns the first non-null error, or null if all pass. */
    public static String firstOf(String... errors) {
        for (String e : errors) if (e != null) return e;
        return null;
    }

    /** Throws if any of the given errors is non-null. */
    public static void check(String... errors) {
        String e = firstOf(errors);
        if (e != null) throw new ValidationException(e);
    }
}
