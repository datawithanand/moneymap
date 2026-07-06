package com.moneymap.model;

import java.time.Instant;

/**
 * Directed permission: what the OWNER has chosen to share with the VIEWER
 * (Section 03/04 §2.1, Section 17 §2.3). Sparse: a missing row = NO_ACCESS.
 * Never symmetric, never transitive (§4.4).
 */
public class FamilyPermission {

    public enum Level { NO_ACCESS, SUMMARY_ONLY, CONTACTS_ONLY, FULL_ACCESS }

    private String id;
    private String familyGroupId;
    private String ownerUserId;
    private String viewerUserId;
    private Level standingPermissionLevel = Level.NO_ACCESS;
    private boolean designatedEmergencyContact;
    private Level emergencyAccessLevel;   // SUMMARY_ONLY | CONTACTS_ONLY | FULL_ACCESS when designated (§3.1)
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getFamilyGroupId() { return familyGroupId; }
    public void setFamilyGroupId(String familyGroupId) { this.familyGroupId = familyGroupId; }
    public String getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(String ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getViewerUserId() { return viewerUserId; }
    public void setViewerUserId(String viewerUserId) { this.viewerUserId = viewerUserId; }
    public Level getStandingPermissionLevel() { return standingPermissionLevel; }
    public void setStandingPermissionLevel(Level standingPermissionLevel) { this.standingPermissionLevel = standingPermissionLevel; }
    public boolean isDesignatedEmergencyContact() { return designatedEmergencyContact; }
    public void setDesignatedEmergencyContact(boolean designatedEmergencyContact) { this.designatedEmergencyContact = designatedEmergencyContact; }
    public Level getEmergencyAccessLevel() { return emergencyAccessLevel; }
    public void setEmergencyAccessLevel(Level emergencyAccessLevel) { this.emergencyAccessLevel = emergencyAccessLevel; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
