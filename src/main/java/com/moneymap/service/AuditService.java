package com.moneymap.service;

import com.moneymap.model.AuditLogEntry;
import com.moneymap.model.User;
import com.moneymap.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Audit log writes are synchronous — a sensitive action is not considered complete until
 * its log write succeeds (Section 01 §7.1). Immutability is structural: the repository
 * interface has no update/delete (§7.3).
 */
@Service
public class AuditService {

    public static final String SYSTEM_ACTOR = "SYSTEM";

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(User actor, AuditLogEntry.ActionType actionType, User target, String details) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.setTimestamp(Instant.now());
        entry.setActorUserId(actor == null ? null : actor.getId());
        entry.setActorUsername(actor == null ? SYSTEM_ACTOR : actor.getUsername());
        entry.setActionType(actionType);
        if (target != null) {
            entry.setTargetUserId(target.getId());
            entry.setTargetUsername(target.getUsername());
        }
        entry.setDetails(details);
        repository.save(entry);
    }

    public void logSystem(AuditLogEntry.ActionType actionType, User target, String details) {
        log(null, actionType, target, details);
    }

    public List<AuditLogEntry> findAll() {
        return repository.findAll();
    }
}
