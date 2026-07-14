package com.moneymap.model;

import java.time.Instant;

/** Family invitation (Section 03/04 §1.2, Section 17 §2.2). 14-day expiry, daily job. */
public class FamilyInvitation {

    public enum Status { PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED }

    private String id;
    private String familyGroupId;
    private String invitedByUserId;
    private String inviteeUserId;    // set at send time if resolvable, or later via email match (§1.2)
    private String inviteeEmail;
    private String inviteeUsername;
    private String relationship;   // e.g. Spouse, Father — as labeled by the inviter, applied to the invitee on accept
    private Status status = Status.PENDING;
    private Instant expiresAt;
    private Instant createdAt;
    private Instant respondedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFamilyGroupId() { return familyGroupId; }
    public void setFamilyGroupId(String familyGroupId) { this.familyGroupId = familyGroupId; }
    public String getInvitedByUserId() { return invitedByUserId; }
    public void setInvitedByUserId(String invitedByUserId) { this.invitedByUserId = invitedByUserId; }
    public String getInviteeUserId() { return inviteeUserId; }
    public void setInviteeUserId(String inviteeUserId) { this.inviteeUserId = inviteeUserId; }
    public String getInviteeEmail() { return inviteeEmail; }
    public void setInviteeEmail(String inviteeEmail) { this.inviteeEmail = inviteeEmail; }
    public String getInviteeUsername() { return inviteeUsername; }
    public void setInviteeUsername(String inviteeUsername) { this.inviteeUsername = inviteeUsername; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public void setRespondedAt(Instant respondedAt) { this.respondedAt = respondedAt; }
}
