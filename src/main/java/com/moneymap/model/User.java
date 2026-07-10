package com.moneymap.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * User record — schema per PRD Section 17 §1.1 / Section 01 §9.
 * One record per login (Admin, Regular, or Family Member — role attribute, not separate types).
 */
public class User {

    public enum Role { ADMIN, REGULAR, FAMILY_MEMBER }
    public enum Status { ACTIVE, DISABLED, PENDING_DELETION }
    public enum CurrencyPreference { INR, USD }
    public enum NumberFormat { INDIAN, INTERNATIONAL }
    public enum Theme { GREEN, SLATE_BLUE, MAROON, FOREST_COPPER, MIDNIGHT }

    private String id;
    private String username;          // immutable, unique, case-insensitive
    private String email;             // unique, case-insensitive
    private String fullName;
    private String passwordHash;      // BCrypt, cost 10 — the ONLY password field ever persisted
    private Role role = Role.REGULAR;
    private Status status = Status.ACTIVE;
    private Instant deletionRequestedAt;
    private Instant scheduledPurgeAt;
    private boolean mustChangePassword;
    private boolean onboardingCompleted;
    private int onboardingStep = 1;  // wizard resume point, persisted after each step (Section 01 §1.2)
    private int failedLoginAttempts;
    private Instant lockedUntil;
    private CurrencyPreference currencyPreference = CurrencyPreference.INR;
    private String dateFormat = "DD/MM/YYYY";  // DD/MM/YYYY | MM/DD/YYYY | YYYY-MM-DD
    private NumberFormat numberFormat = NumberFormat.INDIAN;
    private Theme theme = Theme.GREEN;
    private String profilePhotoPath;
    private boolean notifyInApp = true;
    private boolean notifyEmail = false;
    private BigDecimal goldRate24kPerGram;
    private BigDecimal goldRate22kPerGram;
    private BigDecimal usdInrExchangeRate;
    private int emergencyDenyWindowDays = 7;   // 1–30 (Section 03/04 §3.3)
    private String familyGroupId;
    private boolean familyAdmin;
    private String familyRelationship;   // e.g. Spouse, Father, Mother — free label, set/edited by the Family Admin
    private Instant lastActiveAt;
    private Instant createdAt;
    private Instant updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getDeletionRequestedAt() { return deletionRequestedAt; }
    public void setDeletionRequestedAt(Instant deletionRequestedAt) { this.deletionRequestedAt = deletionRequestedAt; }
    public Instant getScheduledPurgeAt() { return scheduledPurgeAt; }
    public void setScheduledPurgeAt(Instant scheduledPurgeAt) { this.scheduledPurgeAt = scheduledPurgeAt; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    public boolean isOnboardingCompleted() { return onboardingCompleted; }
    public void setOnboardingCompleted(boolean onboardingCompleted) { this.onboardingCompleted = onboardingCompleted; }
    public int getOnboardingStep() { return onboardingStep; }
    public void setOnboardingStep(int onboardingStep) { this.onboardingStep = onboardingStep; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) { this.failedLoginAttempts = failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(Instant lockedUntil) { this.lockedUntil = lockedUntil; }
    public CurrencyPreference getCurrencyPreference() { return currencyPreference; }
    public void setCurrencyPreference(CurrencyPreference currencyPreference) { this.currencyPreference = currencyPreference; }
    public String getDateFormat() { return dateFormat; }
    public void setDateFormat(String dateFormat) { this.dateFormat = dateFormat; }
    public NumberFormat getNumberFormat() { return numberFormat; }
    public void setNumberFormat(NumberFormat numberFormat) { this.numberFormat = numberFormat; }
    public Theme getTheme() { return theme; }
    public void setTheme(Theme theme) { this.theme = theme; }
    public String getProfilePhotoPath() { return profilePhotoPath; }
    public void setProfilePhotoPath(String profilePhotoPath) { this.profilePhotoPath = profilePhotoPath; }
    public boolean isNotifyInApp() { return notifyInApp; }
    public void setNotifyInApp(boolean notifyInApp) { this.notifyInApp = notifyInApp; }
    public boolean isNotifyEmail() { return notifyEmail; }
    public void setNotifyEmail(boolean notifyEmail) { this.notifyEmail = notifyEmail; }
    public BigDecimal getGoldRate24kPerGram() { return goldRate24kPerGram; }
    public void setGoldRate24kPerGram(BigDecimal goldRate24kPerGram) { this.goldRate24kPerGram = goldRate24kPerGram; }
    public BigDecimal getGoldRate22kPerGram() { return goldRate22kPerGram; }
    public void setGoldRate22kPerGram(BigDecimal goldRate22kPerGram) { this.goldRate22kPerGram = goldRate22kPerGram; }
    public BigDecimal getUsdInrExchangeRate() { return usdInrExchangeRate; }
    public void setUsdInrExchangeRate(BigDecimal usdInrExchangeRate) { this.usdInrExchangeRate = usdInrExchangeRate; }
    public int getEmergencyDenyWindowDays() { return emergencyDenyWindowDays; }
    public void setEmergencyDenyWindowDays(int emergencyDenyWindowDays) { this.emergencyDenyWindowDays = emergencyDenyWindowDays; }
    public String getFamilyGroupId() { return familyGroupId; }
    public void setFamilyGroupId(String familyGroupId) { this.familyGroupId = familyGroupId; }
    public boolean isFamilyAdmin() { return familyAdmin; }
    public void setFamilyAdmin(boolean familyAdmin) { this.familyAdmin = familyAdmin; }
    public String getFamilyRelationship() { return familyRelationship; }
    public void setFamilyRelationship(String familyRelationship) { this.familyRelationship = familyRelationship; }
    public Instant getLastActiveAt() { return lastActiveAt; }
    public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
