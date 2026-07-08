package com.moneymap.module;

import com.moneymap.model.asset.OwnedRecord;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Binds form parameters to an entity per the module's FieldSpecs (server-side authority). */
public final class RecordBinder {

    private static final List<String> COMMON =
            List.of("familyMemberTag", "nominee", "customerCareNumber", "customerCareEmail", "portalUrl", "branchAddress",
                    "schemeCode");

    private RecordBinder() {}

    /** Returns a field-level error map; empty = bound successfully. */
    public static Map<String, String> bind(OwnedRecord entity, List<FieldSpec> fields, Map<String, String> params) {
        Map<String, String> errors = new java.util.LinkedHashMap<>();
        BeanWrapper bw = new BeanWrapperImpl(entity);
        for (FieldSpec f : fields) {
            String raw = trimToNull(params.get(f.name()));
            if ("checkbox".equals(f.type())) {
                bw.setPropertyValue(f.name(), params.get(f.name()) != null);
                continue;
            }
            if (raw == null) {
                if (f.required()) errors.put(f.name(), f.label() + " is required.");
                else setNullSafe(bw, f.name());
                continue;
            }
            try {
                Class<?> pt = bw.getPropertyType(f.name());
                Object value;
                if (pt == BigDecimal.class) value = new BigDecimal(raw);
                else if (pt == Integer.class || pt == int.class) value = Integer.valueOf(raw);
                else if (pt == LocalDate.class) value = LocalDate.parse(raw);
                else value = raw;
                if (f.options() != null && value instanceof String s && !f.options().contains(s)) {
                    errors.put(f.name(), "Invalid value for " + f.label() + ".");
                    continue;
                }
                if (value instanceof BigDecimal bd && bd.signum() < 0) {
                    errors.put(f.name(), f.label() + " cannot be negative.");
                    continue;
                }
                if (value instanceof Integer i && i < 0) {
                    errors.put(f.name(), f.label() + " cannot be negative.");
                    continue;
                }
                bw.setPropertyValue(f.name(), value);
            } catch (Exception e) {
                errors.put(f.name(), "Invalid value for " + f.label() + ".");
            }
        }
        for (String c : COMMON) {
            if (params.containsKey(c) && bw.isWritableProperty(c)) {
                String v = trimToNull(params.get(c));
                if (c.equals("familyMemberTag") && v == null) v = "Self";
                bw.setPropertyValue(c, v);
            }
        }
        return errors;
    }

    private static void setNullSafe(BeanWrapper bw, String name) {
        Class<?> pt = bw.getPropertyType(name);
        if (pt != null && !pt.isPrimitive()) bw.setPropertyValue(name, null);
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
