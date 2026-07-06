package com.moneymap.model;

import java.time.Instant;

/** Remember-me token — Section 01 §2.2, Section 17 §1.3. Only the hash of the raw cookie token is stored. */
public class RememberMeToken {
    private String id;
    private String userId;
    private String tokenHash;   // BCrypt hash of the raw token stored in the cookie
    private Instant expiresAt;
    private Instant createdAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
