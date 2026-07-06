package com.moneymap.model;

import java.time.Instant;

/**
 * Audit log entry — append-only, immutable (Section 01 §7, Section 17 §1.4).
 * The repository interface for this collection exposes only save/find — no update/delete, ever.
 */
public class AuditLogEntry {

    /** Enum per Section 01 §9, extended by Section 03/04 §8. */
    public enum ActionType {
        PASSWORD_RESET,
        VIEW_USER_PORTFOLIO,
        ACCOUNT_DISABLED,
        ACCOUNT_ENABLED,
        ACCOUNT_PURGED,
        ACCOUNT_RESTORED,
        SETTINGS_CHANGED,
        VIEW_FAMILY_MEMBERSHIP,
        EMERGENCY_ACCESS_REQUESTED,
        EMERGENCY_ACCESS_APPROVED,
        EMERGENCY_ACCESS_DENIED,
        EMERGENCY_ACCESS_AUTO_GRANTED,
        EMERGENCY_ACCESS_REVOKED
    }

    private String id;
    private Instant timestamp;
    private String actorUserId;     // null/"SYSTEM" for scheduled-job entries
    private String actorUsername;
    private ActionType actionType;
    private String targetUserId;
    private String targetUsername;
    private String details;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
    public String getActorUserId() { return actorUserId; }
    public void setActorUserId(String actorUserId) { this.actorUserId = actorUserId; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String actorUsername) { this.actorUsername = actorUsername; }
    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }
    public String getTargetUserId() { return targetUserId; }
    public void setTargetUserId(String targetUserId) { this.targetUserId = targetUserId; }
    public String getTargetUsername() { return targetUsername; }
    public void setTargetUsername(String targetUsername) { this.targetUsername = targetUsername; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
