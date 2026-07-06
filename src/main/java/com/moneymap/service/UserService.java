package com.moneymap.service;

import com.moneymap.model.GlobalSettings;
import com.moneymap.model.HouseholdMember;
import com.moneymap.model.InviteToken;
import com.moneymap.model.User;
import com.moneymap.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registration, profile, preferences, password change, setup wizard, soft delete
 * (Section 01 §1, §4.1, §5).
 */
@Service
public class UserService {

    private final UserRepository users;
    private final HouseholdMemberRepository householdMembers;
    private final RememberMeTokenRepository rememberMeTokens;
    private final GlobalSettingsRepository globalSettings;
    private final InviteTokenRepository inviteTokens;
    private final SessionRegistry sessionRegistry;
    private final BCryptPasswordEncoder encoder;
    private final Path uploadsDir;
    private final org.springframework.beans.factory.ObjectProvider<FamilyService> familyService;

    public UserService(UserRepository users,
                       HouseholdMemberRepository householdMembers,
                       RememberMeTokenRepository rememberMeTokens,
                       GlobalSettingsRepository globalSettings,
                       InviteTokenRepository inviteTokens,
                       SessionRegistry sessionRegistry,
                       BCryptPasswordEncoder encoder,
                       org.springframework.beans.factory.ObjectProvider<FamilyService> familyService,
                       @Value("${moneymap.data-dir}") String dataDir) {
        this.familyService = familyService;
        this.users = users;
        this.householdMembers = householdMembers;
        this.rememberMeTokens = rememberMeTokens;
        this.globalSettings = globalSettings;
        this.inviteTokens = inviteTokens;
        this.sessionRegistry = sessionRegistry;
        this.encoder = encoder;
        this.uploadsDir = Paths.get(dataDir, "uploads", "profile-photos");
    }

    // ── Registration (§1.1) ──────────────────────────────────────────────────

    /** Returns field-level errors; empty map = success (user created). */
    public Map<String, String> register(String fullName, String username, String email,
                                        String password, String confirmPassword, String inviteToken,
                                        StringBuilder createdUserId) {
        Map<String, String> errors = new LinkedHashMap<>();
        GlobalSettings settings = globalSettings.get();

        InviteToken invite = null;
        if (settings.getRegistrationMode() == GlobalSettings.RegistrationMode.INVITE_ONLY) {
            invite = inviteTokens.findByToken(inviteToken)
                    .filter(t -> t.getUsedAt() == null)
                    .filter(t -> t.getExpiresAt() == null || t.getExpiresAt().isAfter(Instant.now()))
                    .orElse(null);
            if (invite == null) {
                errors.put("_global", "Registration is by invitation only.");
                return errors;
            }
        }

        if (!Validators.validFullName(fullName)) {
            errors.put("fullName", "2–100 characters; letters, spaces, periods, hyphens and apostrophes only.");
        }
        if (!Validators.validUsername(username)) {
            errors.put("username", "3–30 characters; letters, digits, underscore and period only.");
        } else if (users.findByUsernameIgnoreCase(username).isPresent()) {
            errors.put("username", "This username is already taken.");
        }
        if (!Validators.validEmail(email)) {
            errors.put("email", "Please enter a valid email address.");
        } else if (users.findByEmailIgnoreCase(email).isPresent()) {
            errors.put("email", "This email is already registered.");
        }
        if (!Validators.validPassword(password)) {
            errors.put("password", Validators.passwordRuleText());
        }
        if (password != null && !password.equals(confirmPassword)) {
            errors.put("confirmPassword", "Passwords do not match.");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        User user = new User();
        user.setFullName(fullName.trim());
        user.setUsername(username);
        user.setEmail(email.trim());
        user.setPasswordHash(encoder.encode(password));
        user.setRole(User.Role.REGULAR);
        user.setStatus(User.Status.ACTIVE);
        user.setMustChangePassword(false);
        user.setOnboardingCompleted(false);
        user.setCurrencyPreference(settings.getDefaultCurrency());
        user.setTheme(settings.getDefaultTheme());
        User saved = users.save(user);
        seedSelfHouseholdMember(saved.getId());

        if (invite != null) {
            invite.setUsedByUserId(saved.getId());
            invite.setUsedAt(Instant.now());
            inviteTokens.save(invite);
        }
        // Attach any family invitation sent to this email before the account existed (Section 03/04 §1.2)
        FamilyService family = familyService.getIfAvailable();
        if (family != null) family.attachEmailInvitations(saved);
        createdUserId.append(saved.getId());
        return errors;
    }

    /** Every owner's Household Members list is seeded with an undeletable "Self" entry (Section 17 §1.7). */
    public void seedSelfHouseholdMember(String ownerId) {
        boolean hasSelf = householdMembers.findByOwnerId(ownerId).stream()
                .anyMatch(HouseholdMember::isDefaultEntry);
        if (!hasSelf) {
            HouseholdMember self = new HouseholdMember();
            self.setOwnerId(ownerId);
            self.setLabel("Self");
            self.setDefaultEntry(true);
            householdMembers.save(self);
        }
    }

    // ── Password change (§4.1 and forced change §1.3) ────────────────────────

    /** Returns error message or null on success. */
    public String changePassword(User user, String currentPassword, String newPassword, String confirmPassword) {
        if (!encoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            return "Current password is incorrect.";
        }
        if (!Validators.validPassword(newPassword)) {
            return Validators.passwordRuleText();
        }
        if (encoder.matches(newPassword, user.getPasswordHash())) {
            return "New password must be different from your current password.";
        }
        if (!newPassword.equals(confirmPassword)) {
            return "Passwords do not match.";
        }
        user.setPasswordHash(encoder.encode(newPassword));
        user.setMustChangePassword(false);
        users.save(user);
        // Invalidate other sessions and remember-me tokens; the current session stays valid (§4.1).
        rememberMeTokens.deleteByUserId(user.getId());
        return null;
    }

    // ── Setup wizard (§1.2) ──────────────────────────────────────────────────

    public void wizardStep1Currency(User user, User.CurrencyPreference currency) {
        user.setCurrencyPreference(currency);
        user.setOnboardingStep(Math.max(user.getOnboardingStep(), 2));
        users.save(user);
    }

    public void wizardStep2Theme(User user, User.Theme theme) {
        user.setTheme(theme);
        user.setOnboardingStep(Math.max(user.getOnboardingStep(), 3));
        users.save(user);
    }

    public void wizardStepPersist(User user) {
        users.save(user);
    }

    public void wizardStep3FamilySkip(User user) {
        // Create/Join hands off to Section 03's flows (later increment); Skip is always allowed.
        user.setOnboardingStep(Math.max(user.getOnboardingStep(), 4));
        users.save(user);
    }

    /** Step 4 — gold rate; completes onboarding. Splits per Section 01B into 24K (entered) with 22K defaulted alongside. */
    public String wizardStep4GoldRate(User user, BigDecimal rate24k, BigDecimal rate22k) {
        if (rate24k == null || rate24k.signum() <= 0) {
            return "Please enter a positive gold rate.";
        }
        user.setGoldRate24kPerGram(rate24k);
        user.setGoldRate22kPerGram(rate22k != null && rate22k.signum() > 0 ? rate22k : rate24k);
        user.setOnboardingCompleted(true);
        users.save(user);
        return null;
    }

    // ── Profile & preferences (§5.1, §5.2, §5.3, §5.4, 01B) ─────────────────

    public Map<String, String> updateProfile(User user, String fullName, String email, MultipartFile photo, boolean removePhoto) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!Validators.validFullName(fullName)) {
            errors.put("fullName", "2–100 characters; letters, spaces, periods, hyphens and apostrophes only.");
        }
        if (!Validators.validEmail(email)) {
            errors.put("email", "Please enter a valid email address.");
        } else {
            Optional<User> existing = users.findByEmailIgnoreCase(email);
            if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
                errors.put("email", "This email is already registered to another account.");
            }
        }
        if (!errors.isEmpty()) return errors;

        user.setFullName(fullName.trim());
        user.setEmail(email.trim());

        if (removePhoto) {
            deletePhotoFile(user);
            user.setProfilePhotoPath(null);
        } else if (photo != null && !photo.isEmpty()) {
            String contentType = photo.getContentType() == null ? "" : photo.getContentType();
            String ext = switch (contentType) {
                case "image/jpeg" -> "jpg";
                case "image/png" -> "png";
                case "image/webp" -> "webp";
                default -> null;
            };
            if (ext == null) {
                errors.put("photo", "Only JPEG, PNG or WebP images are accepted.");
                return errors;
            }
            try {
                Files.createDirectories(uploadsDir);
                deletePhotoFile(user);
                Path target = uploadsDir.resolve(user.getId() + "." + ext);
                try (var in = photo.getInputStream()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                user.setProfilePhotoPath("uploads/profile-photos/" + user.getId() + "." + ext);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to store profile photo", e);
            }
        }
        users.save(user);   // name/email changes do NOT invalidate sessions (§5.1)
        return errors;
    }

    private void deletePhotoFile(User user) {
        if (user.getProfilePhotoPath() == null) return;
        try {
            Path old = uploadsDir.resolve(Paths.get(user.getProfilePhotoPath()).getFileName());
            Files.deleteIfExists(old);
        } catch (IOException ignored) {
        }
    }

    public void updatePreferences(User user, User.CurrencyPreference currency, String dateFormat,
                                  User.NumberFormat numberFormat, User.Theme theme) {
        user.setCurrencyPreference(currency);
        if (dateFormat != null && (dateFormat.equals("DD/MM/YYYY") || dateFormat.equals("MM/DD/YYYY") || dateFormat.equals("YYYY-MM-DD"))) {
            user.setDateFormat(dateFormat);
        }
        user.setNumberFormat(numberFormat);
        user.setTheme(theme);
        users.save(user);
    }

    public void updateNotificationPreferences(User user, boolean notifyInApp, boolean notifyEmail) {
        user.setNotifyInApp(notifyInApp);
        // Email channel requires instance SMTP (§5.4) — persisting the wish is harmless, but gate it:
        user.setNotifyEmail(notifyEmail && globalSettings.get().isSmtpEnabled());
        users.save(user);
    }

    public String updateRates(User user, BigDecimal rate24k, BigDecimal rate22k, BigDecimal usdInr) {
        if (rate24k == null || rate24k.signum() <= 0 || rate22k == null || rate22k.signum() <= 0
                || usdInr == null || usdInr.signum() <= 0) {
            return "All rates must be positive numbers.";
        }
        user.setGoldRate24kPerGram(rate24k);
        user.setGoldRate22kPerGram(rate22k);
        user.setUsdInrExchangeRate(usdInr);
        users.save(user);
        return null;
    }

    // ── Account deletion — soft delete (§5.5) ────────────────────────────────

    /** Returns error message or null. On success the user is logged out everywhere. */
    public String requestDeletion(User user, String typedUsername, String currentPassword) {
        if (!user.getUsername().equalsIgnoreCase(typedUsername == null ? "" : typedUsername.trim())) {
            return "Username confirmation does not match.";
        }
        if (!encoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            return "Password is incorrect.";
        }
        if (user.getRole() == User.Role.ADMIN && countActiveAdminsExcluding(user.getId()) == 0) {
            return "This is the only administrator account on the instance — it cannot be deleted. "
                    + "Promote another administrator first.";
        }
        Instant now = Instant.now();
        user.setStatus(User.Status.PENDING_DELETION);
        user.setDeletionRequestedAt(now);
        user.setScheduledPurgeAt(now.plus(Duration.ofDays(30)));
        users.save(user);
        rememberMeTokens.deleteByUserId(user.getId());
        sessionRegistry.invalidateUser(user.getId());
        return null;
    }

    public long countActiveAdminsExcluding(String excludeUserId) {
        return users.findAll().stream()
                .filter(u -> u.getRole() == User.Role.ADMIN)
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .filter(u -> !u.getId().equals(excludeUserId))
                .count();
    }
}
