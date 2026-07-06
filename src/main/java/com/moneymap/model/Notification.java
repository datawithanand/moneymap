package com.moneymap.model;

import java.time.Instant;

/** In-app notification (Section 03/04 §5, Section 17 §1.6). Message is fully rendered text. */
public class Notification {

    public enum Type {
        EMERGENCY_ACCESS_REQUESTED, EMERGENCY_ACCESS_APPROVED, EMERGENCY_ACCESS_DENIED,
        EMERGENCY_ACCESS_AUTO_APPROVAL_PENDING, EMERGENCY_ACCESS_AUTO_APPROVED,
        EMERGENCY_ACCESS_REVOKED, EMERGENCY_ACCESS_EXPIRED,
        FAMILY_INVITATION_RECEIVED, FAMILY_INVITATION_DECLINED,
        FAMILY_MEMBER_JOINED, FAMILY_MEMBER_LEFT, FAMILY_MEMBER_REMOVED,
        FAMILY_GROUP_DISSOLVED, PERMISSION_LEVEL_CHANGED,
        ASSET_MATURITY_APPROACHING, PREMIUM_DUE_APPROACHING, PAYMENT_DUE_APPROACHING
    }

    private String id;
    private String userId;          // recipient
    private Type type;
    private String relatedEntityId;
    private String message;
    private boolean isRead;
    private boolean emailSent;
    private Instant createdAt;
    private Instant readAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }
    public String getRelatedEntityId() { return relatedEntityId; }
    public void setRelatedEntityId(String relatedEntityId) { this.relatedEntityId = relatedEntityId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }
    public boolean isEmailSent() { return emailSent; }
    public void setEmailSent(boolean emailSent) { this.emailSent = emailSent; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getReadAt() { return readAt; }
    public void setReadAt(Instant readAt) { this.readAt = readAt; }
}
