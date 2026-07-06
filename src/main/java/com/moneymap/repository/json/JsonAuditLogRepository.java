package com.moneymap.repository.json;

import com.moneymap.model.AuditLogEntry;
import com.moneymap.repository.AuditLogRepository;
import com.moneymap.repository.JsonCollectionStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Repository
public class JsonAuditLogRepository implements AuditLogRepository {

    private static final String COLLECTION = "audit_log";

    private final JsonCollectionStore store;

    public JsonAuditLogRepository(JsonCollectionStore store) {
        this.store = store;
    }

    @Override
    public AuditLogEntry save(AuditLogEntry entry) {
        return store.mutate(COLLECTION, AuditLogEntry.class, list -> {
            entry.setId(UUID.randomUUID().toString());
            if (entry.getTimestamp() == null) {
                entry.setTimestamp(Instant.now());
            }
            list.add(entry);   // append-only
            return entry;
        });
    }

    @Override
    public List<AuditLogEntry> findAll() {
        List<AuditLogEntry> all = store.readAll(COLLECTION, AuditLogEntry.class);
        all.sort(Comparator.comparing(AuditLogEntry::getTimestamp).reversed());
        return all;
    }
}
