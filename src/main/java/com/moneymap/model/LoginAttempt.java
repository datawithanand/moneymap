package com.moneymap.model;

import java.time.Instant;

/** Failed-login telemetry — rolling 30-day retention (Section 02, Section 17 §1.5). */
public class LoginAttempt {
    private String id;
    private String usernameOrEmailAttempted;
    private Instant timestamp;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsernameOrEmailAttempted() { return usernameOrEmailAttempted; }
    public void setUsernameOrEmailAttempted(String usernameOrEmailAttempted) { this.usernameOrEmailAttempted = usernameOrEmailAttempted; }
    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
