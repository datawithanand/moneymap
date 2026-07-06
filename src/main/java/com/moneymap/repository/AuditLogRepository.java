package com.moneymap.repository;

import com.moneymap.model.AuditLogEntry;

import java.util.List;

/**
 * STRUCTURAL IMMUTABILITY (Section 01 §7.3): this interface deliberately exposes only
 * save and find operations. It must NEVER gain an update or delete method — the
 * append-only guarantee is enforced by the interface shape available to the service
 * layer, not merely by UI omission.
 */
public interface AuditLogRepository {

    AuditLogEntry save(AuditLogEntry entry);

    /** All entries, newest first. */
    List<AuditLogEntry> findAll();
}
