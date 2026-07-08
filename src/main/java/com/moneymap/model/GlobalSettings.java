package com.moneymap.model;

import java.time.Instant;

/**
 * Instance-wide settings singleton — exactly one object per deployment,
 * stored as a single JSON object in global_settings.json (Section 01 §6.6, Section 01B, Section 17 §1.2).
 */
public class GlobalSettings {

    public enum RegistrationMode { OPEN, INVITE_ONLY }

    private String instanceName = "MoneyMap";
    private RegistrationMode registrationMode = RegistrationMode.OPEN;
    private int sessionTimeoutMinutes = 60;      // bounded 5–1440
    private int maxFailedLoginAttempts = 5;      // bounded 3–10
    private int lockoutDurationMinutes = 15;     // bounded 1–1440
    private boolean smtpEnabled = false;
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpPasswordEncrypted;        // reversible AES — deliberate exception, Section 01B
    private String smtpFromAddress;
    private User.CurrencyPreference defaultCurrency = User.CurrencyPreference.INR;
    private User.Theme defaultTheme = User.Theme.GREEN;
    private Instant lastBackupMarkedAt;
    private Instant fundMasterLastSyncedAt;
    private Integer fundMasterSchemeCount;
    private Instant updatedAt;

    public String getInstanceName() { return instanceName; }
    public void setInstanceName(String instanceName) { this.instanceName = instanceName; }
    public RegistrationMode getRegistrationMode() { return registrationMode; }
    public void setRegistrationMode(RegistrationMode registrationMode) { this.registrationMode = registrationMode; }
    public int getSessionTimeoutMinutes() { return sessionTimeoutMinutes; }
    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) { this.sessionTimeoutMinutes = sessionTimeoutMinutes; }
    public int getMaxFailedLoginAttempts() { return maxFailedLoginAttempts; }
    public void setMaxFailedLoginAttempts(int maxFailedLoginAttempts) { this.maxFailedLoginAttempts = maxFailedLoginAttempts; }
    public int getLockoutDurationMinutes() { return lockoutDurationMinutes; }
    public void setLockoutDurationMinutes(int lockoutDurationMinutes) { this.lockoutDurationMinutes = lockoutDurationMinutes; }
    public boolean isSmtpEnabled() { return smtpEnabled; }
    public void setSmtpEnabled(boolean smtpEnabled) { this.smtpEnabled = smtpEnabled; }
    public String getSmtpHost() { return smtpHost; }
    public void setSmtpHost(String smtpHost) { this.smtpHost = smtpHost; }
    public Integer getSmtpPort() { return smtpPort; }
    public void setSmtpPort(Integer smtpPort) { this.smtpPort = smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public void setSmtpUsername(String smtpUsername) { this.smtpUsername = smtpUsername; }
    public String getSmtpPasswordEncrypted() { return smtpPasswordEncrypted; }
    public void setSmtpPasswordEncrypted(String smtpPasswordEncrypted) { this.smtpPasswordEncrypted = smtpPasswordEncrypted; }
    public String getSmtpFromAddress() { return smtpFromAddress; }
    public void setSmtpFromAddress(String smtpFromAddress) { this.smtpFromAddress = smtpFromAddress; }
    public User.CurrencyPreference getDefaultCurrency() { return defaultCurrency; }
    public void setDefaultCurrency(User.CurrencyPreference defaultCurrency) { this.defaultCurrency = defaultCurrency; }
    public User.Theme getDefaultTheme() { return defaultTheme; }
    public void setDefaultTheme(User.Theme defaultTheme) { this.defaultTheme = defaultTheme; }
    public Instant getLastBackupMarkedAt() { return lastBackupMarkedAt; }
    public void setLastBackupMarkedAt(Instant lastBackupMarkedAt) { this.lastBackupMarkedAt = lastBackupMarkedAt; }
    public Instant getFundMasterLastSyncedAt() { return fundMasterLastSyncedAt; }
    public void setFundMasterLastSyncedAt(Instant fundMasterLastSyncedAt) { this.fundMasterLastSyncedAt = fundMasterLastSyncedAt; }
    public Integer getFundMasterSchemeCount() { return fundMasterSchemeCount; }
    public void setFundMasterSchemeCount(Integer fundMasterSchemeCount) { this.fundMasterSchemeCount = fundMasterSchemeCount; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
