package com.moneymap.model;

import java.time.Instant;

/**
 * Per-owner household label list that populates the familyMemberTag dropdown in every
 * asset module (Section 05 opening note, Section 17 §1.7). Carries no login, permission,
 * or sharing implication. Every owner is seeded with an undeletable "Self" entry.
 */
public class HouseholdMember {
    private String id;
    private String ownerId;
    private String label;          // 2–50 chars, unique per owner (case-insensitive)
    private boolean defaultEntry;  // true only for the seeded "Self" entry — undeletable
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public boolean isDefaultEntry() { return defaultEntry; }
    public void setDefaultEntry(boolean defaultEntry) { this.defaultEntry = defaultEntry; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
