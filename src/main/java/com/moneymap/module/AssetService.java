package com.moneymap.module;

import com.moneymap.model.User;
import com.moneymap.model.asset.OwnedRecord;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Row/label helpers for the metadata-driven asset views. */
@Service
public class AssetService {

    /** List-view rows: bean properties + computed columns + id, filtered by owner and optional tag. */
    public List<Map<String, Object>> rows(ModuleDef<?> module, String ownerId, String tagFilter, User viewerBase) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object record : module.repo.findWhere(r -> ownerId.equals(((OwnedRecord) r).getOwnerId()))) {
            OwnedRecord owned = (OwnedRecord) record;
            if (tagFilter != null && !tagFilter.isBlank() && !tagFilter.equals(owned.getFamilyMemberTag())) continue;
            out.add(row(module, owned, viewerBase));
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, Object> row(ModuleDef<?> module, OwnedRecord record, User owner) {
        Map<String, Object> row = new LinkedHashMap<>();
        BeanWrapper bw = new BeanWrapperImpl(record);
        row.put("id", record.getId());
        row.put("familyMemberTag", record.getFamilyMemberTag());
        for (String col : module.listColumns) {
            var computed = ((Map<String, java.util.function.BiFunction>) (Map) module.computed).get(col);
            if (computed != null) {
                row.put(col, computed.apply(record, owner));
            } else if (bw.isReadableProperty(col)) {
                row.put(col, bw.getPropertyValue(col));
            }
        }
        if (module.value != null) {
            row.put("_value", module.valueOf(record, owner));
        }
        return row;
    }

    /** Form values keyed by field name (ISO dates, plain strings) for edit rendering. */
    public Map<String, Object> formValues(ModuleDef<?> module, OwnedRecord record) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (record == null) return values;
        BeanWrapper bw = new BeanWrapperImpl(record);
        for (FieldSpec f : module.fields) {
            values.put(f.name(), bw.getPropertyValue(f.name()));
        }
        for (String c : List.of("familyMemberTag", "nominee", "customerCareNumber", "customerCareEmail", "portalUrl", "branchAddress")) {
            values.put(c, bw.getPropertyValue(c));
        }
        values.put("labels", String.join(", ", record.getLabels()));
        return values;
    }

    public String labelFor(ModuleDef<?> module, String col) {
        for (FieldSpec f : module.fields) {
            if (f.name().equals(col)) return f.label();
        }
        return switch (col) {
            case "maskedNumber" -> "Account Number";
            case "maturityValue" -> "Maturity Value";
            case "currentValue" -> "Current Value";
            case "daysToMaturity" -> "Days to Maturity";
            case "daysToPremium" -> "Days to Premium";
            case "daysToRenewal" -> "Days to Renewal";
            case "invested" -> "Invested";
            case "gainLoss" -> "Gain / Loss";
            case "vestedValue" -> "Vested Value";
            case "unvestedValue" -> "Unvested Value (info)";
            case "totalDeposited" -> "Total Deposited";
            case "monthsRemaining" -> "Months Remaining";
            case "status" -> "Status";
            case "familyMemberTag" -> "Family Member";
            case "nominalSip" -> "Required SIP (nominal)";
            case "inflationAdjustedSip" -> "Required SIP (inflation-adjusted)";
            case "grossMonthly" -> "Gross Monthly";
            case "maturityDate" -> "Maturity Date";
            default -> humanize(col);
        };
    }

    private String humanize(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c) && sb.length() > 0) sb.append(' ');
            sb.append(sb.length() == 0 ? Character.toUpperCase(c) : c);
        }
        return sb.toString();
    }
}
