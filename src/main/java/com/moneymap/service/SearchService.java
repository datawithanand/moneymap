package com.moneymap.service;

import com.moneymap.model.User;
import com.moneymap.model.expense.Document;
import com.moneymap.model.expense.ExpenseEntry;
import com.moneymap.module.AssetService;
import com.moneymap.module.ModuleDef;
import com.moneymap.module.ModuleRegistry;
import com.moneymap.repository.Db;
import com.moneymap.repository.HouseholdMemberRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only keyword search across every existing entity type owned by the current user —
 * assets/liabilities, goals, documents, expense entries, household members. No new tables;
 * this only reads what's already stored elsewhere.
 */
@Service
public class SearchService {

    public record SearchResult(String type, String id, String label, String modulePath) {}

    private final Db db;
    private final ModuleRegistry registry;
    private final AssetService assetService;
    private final HouseholdMemberRepository householdMembers;

    public SearchService(Db db, ModuleRegistry registry, AssetService assetService,
                         HouseholdMemberRepository householdMembers) {
        this.db = db;
        this.registry = registry;
        this.assetService = assetService;
        this.householdMembers = householdMembers;
    }

    public List<SearchResult> search(User user, String query) {
        List<SearchResult> out = new ArrayList<>();
        if (query == null || query.isBlank()) return out;
        String q = query.trim().toLowerCase(Locale.ROOT);
        String ownerId = user.getId();

        for (ModuleDef<?> def : registry.all()) {
            for (Map<String, Object> row : assetService.rows(def, ownerId, null, user)) {
                String label = null;
                boolean matched = false;
                for (String col : def.listColumns) {
                    Object value = row.get(col);
                    if (value == null) continue;
                    if (label == null && value instanceof String s && !s.isBlank()) label = s;
                    if (String.valueOf(value).toLowerCase(Locale.ROOT).contains(q)) matched = true;
                }
                if (matched) {
                    out.add(new SearchResult(def.displayName, String.valueOf(row.get("id")),
                            label != null ? label : def.displayName, def.path));
                }
            }
        }

        for (Document d : db.documents.findWhere(d -> ownerId.equals(d.getOwnerId()))) {
            if (contains(q, d.getOriginalFileName()) || contains(q, d.getDocType())) {
                out.add(new SearchResult("Document", d.getId(),
                        d.getOriginalFileName() != null ? d.getOriginalFileName() : d.getDocType(), "documents"));
            }
        }

        for (ExpenseEntry e : db.expenseEntries.findWhere(e -> ownerId.equals(e.getOwnerId()))) {
            if (contains(q, e.getNote()) || contains(q, e.getCategory())) {
                String label = (e.getCategory() != null ? e.getCategory() : "Expense")
                        + (e.getNote() != null && !e.getNote().isBlank() ? " — " + e.getNote() : "");
                out.add(new SearchResult("Expense Entry", e.getId(), label, "expense-entries"));
            }
        }

        for (var member : householdMembers.findByOwnerId(ownerId)) {
            if (contains(q, member.getLabel())) {
                out.add(new SearchResult("Family Member", member.getId(), member.getLabel(), "family"));
            }
        }

        return out;
    }

    private boolean contains(String query, String value) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
