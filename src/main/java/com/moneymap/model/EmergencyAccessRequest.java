package com.moneymap.model;

import java.time.Instant;

/** Family Vault emergency access request — state machine per Section 03/04 §3, Section 17 §2.4. */
public class EmergencyAccessRequest {

    public enum Status { PENDING, APPROVED, DENIED, AUTO_APPROVED, REVOKED, EXPIRED }

    private String id;
    private String familyGroupId;
    private String requesterId;
    private String ownerId;
    private String reason;                 // required free text, min 10 chars
    private Instant requestedAt;
    private int denyWindowDays;            // snapshot of owner's setting at request time
    private Instant denyWindowExpiresAt;
    private Status status = Status.PENDING;
    private FamilyPermission.Level grantedPermissionLevel;
    private Instant grantExpiresAt;        // grant timestamp + 30 days
    private Instant respondedAt;
    private Instant revokedAt;
    private String revokedByUserId;
    private boolean reminderSent;          // EMERGENCY_ACCESS_AUTO_APPROVAL_PENDING sent once (§3.3)

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFamilyGroupId() { return familyGroupId; }
    public void setFamilyGroupId(String familyGroupId) { this.familyGroupId = familyGroupId; }
    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Instant getRequestedAt() { return requestedAt; }
    public void setRequestedAt(Instant requestedAt) { this.requestedAt = requestedAt; }
    public int getDenyWindowDays() { return denyWindowDays; }
    public void setDenyWindowDays(int denyWindowDays) { this.denyWindowDays = denyWindowDays; }
    public Instant getDenyWindowExpiresAt() { return denyWindowExpiresAt; }
    public void setDenyWindowExpiresAt(Instant denyWindowExpiresAt) { this.denyWindowExpiresAt = denyWindowExpiresAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public FamilyPermission.Level getGrantedPermissionLevel() { return grantedPermissionLevel; }
    public void setGrantedPermissionLevel(FamilyPermission.Level grantedPermissionLevel) { this.grantedPermissionLevel = grantedPermissionLevel; }
    public Instant getGrantExpiresAt() { return grantExpiresAt; }
    public void setGrantExpiresAt(Instant grantExpiresAt) { this.grantExpiresAt = grantExpiresAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public String getRevokedByUserId() { return revokedByUserId; }
    public void setRevokedByUserId(String revokedByUserId) { this.revokedByUserId = revokedByUserId; }
    public boolean isReminderSent() { return reminderSent; }
    public void setReminderSent(boolean reminderSent) { this.reminderSent = reminderSent; }

    public boolean isActiveGrant(Instant now) {
        return (status == Status.APPROVED || status == Status.AUTO_APPROVED)
                && grantExpiresAt != null && grantExpiresAt.isAfter(now);
    }
}
