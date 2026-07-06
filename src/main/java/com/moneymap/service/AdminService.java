package com.moneymap.service;

import com.moneymap.model.AuditLogEntry;
import com.moneymap.model.GlobalSettings;
import com.moneymap.model.InviteToken;
import com.moneymap.model.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moneymap.repository.GlobalSettingsRepository;
import com.moneymap.repository.HouseholdMemberRepository;
import com.moneymap.repository.InviteTokenRepository;
import com.moneymap.repository.RememberMeTokenRepository;
import com.moneymap.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin user management (Section 01 §4.2, §6) — every sensitive action is audit-logged
 * synchronously before it is considered complete (§7.1).
 */
@Service
public class AdminService {

    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "!@#$%^&*";

    private final UserRepository users;
    private final RememberMeTokenRepository rememberMeTokens;
    private final GlobalSettingsRepository globalSettings;
    private final InviteTokenRepository inviteTokens;
    private final HouseholdMemberRepository householdMembers;
    private final SessionRegistry sessionRegistry;
    private final AuditService audit;
    private final UserService userService;
    private final BCryptPasswordEncoder encoder;
    private final ObjectMapper objectMapper;
    private final Path dataDir;
    private final SecureRandom random = new SecureRandom();

    public AdminService(UserRepository users,
                        RememberMeTokenRepository rememberMeTokens,
                        GlobalSettingsRepository globalSettings,
                        InviteTokenRepository inviteTokens,
                        HouseholdMemberRepository householdMembers,
                        SessionRegistry sessionRegistry,
                        AuditService audit,
                        UserService userService,
                        BCryptPasswordEncoder encoder,
                        ObjectMapper objectMapper,
                        @Value("${moneymap.data-dir}") String dataDir) {
        this.users = users;
        this.rememberMeTokens = rememberMeTokens;
        this.globalSettings = globalSettings;
        this.inviteTokens = inviteTokens;
        this.householdMembers = householdMembers;
        this.sessionRegistry = sessionRegistry;
        this.audit = audit;
        this.userService = userService;
        this.encoder = encoder;
        this.objectMapper = objectMapper;
        this.dataDir = Paths.get(dataDir);
    }

    /**
     * Per-user storage usage (§6.5) — computed on demand, never stored. As financial
     * modules land (Sections 05–11A), each module's records are added to this breakdown.
     */
    public Map<String, String> storageBreakdown(User target) {
        Map<String, String> out = new LinkedHashMap<>();
        long total = 0;
        try {
            long userBytes = objectMapper.writeValueAsBytes(target).length;
            total += userBytes;
            out.put("Account record", human(userBytes));
            long hhBytes = objectMapper.writeValueAsBytes(householdMembers.findByOwnerId(target.getId())).length;
            total += hhBytes;
            out.put("Household members", human(hhBytes));
        } catch (Exception ignored) {
        }
        if (target.getProfilePhotoPath() != null) {
            try {
                Path photo = dataDir.resolve(target.getProfilePhotoPath());
                if (Files.exists(photo)) {
                    long size = Files.size(photo);
                    total += size;
                    out.put("Profile photo", human(size));
                }
            } catch (Exception ignored) {
            }
        }
        out.put("Total", human(total));
        return out;
    }

    private String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Admin-initiated password reset (§4.2): generates a temporary password (12 chars incl.
     * ≥1 special), returns the plaintext EXACTLY ONCE for the one-time-reveal modal —
     * never persisted or logged in plaintext. Admins cannot reset their own password here.
     */
    public String resetPassword(User admin, User target) {
        if (admin.getId().equals(target.getId())) {
            throw new IllegalArgumentException("Use self-service Change Password for your own account.");
        }
        String temp = generateTemporaryPassword();
        target.setPasswordHash(encoder.encode(temp));
        target.setMustChangePassword(true);
        users.save(target);
        rememberMeTokens.deleteByUserId(target.getId());
        sessionRegistry.invalidateUser(target.getId());
        audit.log(admin, AuditLogEntry.ActionType.PASSWORD_RESET, target, "Admin-initiated password reset");
        return temp;
    }

    private String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        String all = LOWER + UPPER + DIGITS + SPECIALS;
        sb.append(SPECIALS.charAt(random.nextInt(SPECIALS.length())));
        sb.append(UPPER.charAt(random.nextInt(UPPER.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        for (int i = 3; i < 12; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        // Shuffle
        List<Character> chars = new ArrayList<>();
        for (char c : sb.toString().toCharArray()) chars.add(c);
        java.util.Collections.shuffle(chars, random);
        StringBuilder out = new StringBuilder(12);
        chars.forEach(out::append);
        return out.toString();
    }

    /** Disable (§6.4): ends current access immediately, unlike lockout. */
    public String disable(User admin, User target) {
        if (target.getRole() == User.Role.ADMIN
                && userService.countActiveAdminsExcluding(target.getId()) == 0) {
            return "Cannot disable the only active administrator account.";
        }
        target.setStatus(User.Status.DISABLED);
        users.save(target);
        rememberMeTokens.deleteByUserId(target.getId());
        sessionRegistry.invalidateUser(target.getId());
        audit.log(admin, AuditLogEntry.ActionType.ACCOUNT_DISABLED, target, "Account disabled by administrator");
        return null;
    }

    /** Re-enable (§6.4): clears lockout state as a courtesy; the user must log in again normally. */
    public void enable(User admin, User target) {
        target.setStatus(User.Status.ACTIVE);
        target.setFailedLoginAttempts(0);
        target.setLockedUntil(null);
        users.save(target);
        audit.log(admin, AuditLogEntry.ActionType.ACCOUNT_ENABLED, target, "Account re-enabled by administrator");
    }

    /** Restore a PENDING_DELETION account within the 30-day grace window (§5.5). */
    public void restore(User admin, User target) {
        target.setStatus(User.Status.ACTIVE);
        target.setDeletionRequestedAt(null);
        target.setScheduledPurgeAt(null);
        users.save(target);
        audit.log(admin, AuditLogEntry.ActionType.ACCOUNT_RESTORED, target, "Account restored within deletion grace period");
    }

    /** Audited portfolio view entry (§6.3) — logged at the moment of confirmation. */
    public void logPortfolioView(User admin, User target) {
        audit.log(admin, AuditLogEntry.ActionType.VIEW_USER_PORTFOLIO, target,
                "Admin viewed user's portfolio (read-only mode)");
    }

    /** Global settings update (§6.6) — one SETTINGS_CHANGED audit entry per save, detailing changed fields. */
    public String updateGlobalSettings(User admin, int sessionTimeoutMinutes, int maxFailedLoginAttempts,
                                       int lockoutDurationMinutes, GlobalSettings.RegistrationMode registrationMode,
                                       String instanceName) {
        if (sessionTimeoutMinutes < 5 || sessionTimeoutMinutes > 1440) return "Session timeout must be 5–1440 minutes.";
        if (maxFailedLoginAttempts < 3 || maxFailedLoginAttempts > 10) return "Max failed login attempts must be 3–10.";
        if (lockoutDurationMinutes < 1 || lockoutDurationMinutes > 1440) return "Lockout duration must be 1–1440 minutes.";
        if (instanceName == null || instanceName.trim().length() < 2 || instanceName.trim().length() > 60) {
            return "Instance name must be 2–60 characters.";
        }

        GlobalSettings before = globalSettings.get();
        StringBuilder changes = new StringBuilder();
        if (before.getSessionTimeoutMinutes() != sessionTimeoutMinutes)
            changes.append("sessionTimeoutMinutes: ").append(before.getSessionTimeoutMinutes()).append(" → ").append(sessionTimeoutMinutes).append("; ");
        if (before.getMaxFailedLoginAttempts() != maxFailedLoginAttempts)
            changes.append("maxFailedLoginAttempts: ").append(before.getMaxFailedLoginAttempts()).append(" → ").append(maxFailedLoginAttempts).append("; ");
        if (before.getLockoutDurationMinutes() != lockoutDurationMinutes)
            changes.append("lockoutDurationMinutes: ").append(before.getLockoutDurationMinutes()).append(" → ").append(lockoutDurationMinutes).append("; ");
        if (before.getRegistrationMode() != registrationMode)
            changes.append("registrationMode: ").append(before.getRegistrationMode()).append(" → ").append(registrationMode).append("; ");
        if (!before.getInstanceName().equals(instanceName.trim()))
            changes.append("instanceName: ").append(before.getInstanceName()).append(" → ").append(instanceName.trim()).append("; ");

        globalSettings.update(s -> {
            s.setSessionTimeoutMinutes(sessionTimeoutMinutes);
            s.setMaxFailedLoginAttempts(maxFailedLoginAttempts);
            s.setLockoutDurationMinutes(lockoutDurationMinutes);
            s.setRegistrationMode(registrationMode);
            s.setInstanceName(instanceName.trim());
            return s;
        });
        if (changes.length() > 0) {
            audit.log(admin, AuditLogEntry.ActionType.SETTINGS_CHANGED, null, changes.toString().trim());
        }
        return null;
    }

    /** Generates a single-use invite link token, default 7-day expiry (§6.6). */
    public InviteToken generateInvite(User admin) {
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        InviteToken token = new InviteToken();
        token.setToken(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
        token.setCreatedByUserId(admin.getId());
        token.setExpiresAt(Instant.now().plus(Duration.ofDays(7)));
        return inviteTokens.save(token);
    }
}
