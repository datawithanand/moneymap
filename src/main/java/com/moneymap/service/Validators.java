package com.moneymap.service;

import java.util.regex.Pattern;

/** Validation rules per PRD Section 01 §1.1. */
public final class Validators {

    private Validators() {}

    public static final Pattern USERNAME = Pattern.compile("^[a-zA-Z0-9_.]{3,30}$");
    public static final Pattern FULL_NAME = Pattern.compile("^[\\p{L} .'-]{2,100}$");
    public static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final String SPECIALS = "!@#$%^&*()_+-=[]{}|;:'\",.<>/?";

    public static boolean validUsername(String v) { return v != null && USERNAME.matcher(v).matches(); }

    public static boolean validFullName(String v) { return v != null && FULL_NAME.matcher(v.trim()).matches(); }

    public static boolean validEmail(String v) { return v != null && EMAIL.matcher(v.trim()).matches(); }

    /** Hard rules only: min 8 chars, at least one special character. Strength meter is advisory (§4.3). */
    public static boolean validPassword(String v) {
        if (v == null || v.length() < 8) return false;
        for (char c : v.toCharArray()) {
            if (SPECIALS.indexOf(c) >= 0) return true;
        }
        return false;
    }

    public static String passwordRuleText() {
        return "Minimum 8 characters, including at least one special character.";
    }
}
