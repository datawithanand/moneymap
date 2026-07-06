package com.moneymap.module;

import java.util.List;

/** Declarative form/list field for the metadata-driven asset CRUD (fields per Section 17 schemas). */
public record FieldSpec(
        String name,
        String label,
        String type,          // text | number | date | select | checkbox | textarea
        boolean required,
        List<String> options, // for select
        String hint,
        String visibleIf      // "field=VAL1|VAL2" — client-side conditional visibility (e.g. Loan §09)
) {
    public static FieldSpec text(String name, String label, boolean required) {
        return new FieldSpec(name, label, "text", required, null, null, null);
    }
    public static FieldSpec text(String name, String label, boolean required, String hint) {
        return new FieldSpec(name, label, "text", required, null, hint, null);
    }
    public static FieldSpec num(String name, String label, boolean required) {
        return new FieldSpec(name, label, "number", required, null, null, null);
    }
    public static FieldSpec num(String name, String label, boolean required, String hint) {
        return new FieldSpec(name, label, "number", required, null, hint, null);
    }
    public static FieldSpec date(String name, String label, boolean required) {
        return new FieldSpec(name, label, "date", required, null, null, null);
    }
    public static FieldSpec select(String name, String label, boolean required, String... options) {
        return new FieldSpec(name, label, "select", required, List.of(options), null, null);
    }
    public static FieldSpec check(String name, String label) {
        return new FieldSpec(name, label, "checkbox", false, null, null, null);
    }
    public FieldSpec withVisibleIf(String condition) {
        return new FieldSpec(name, label, type, required, options, hint, condition);
    }
    public FieldSpec withHint(String h) {
        return new FieldSpec(name, label, type, required, options, h, visibleIf);
    }
}
