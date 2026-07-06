package com.moneymap.service;

import com.moneymap.model.GlobalSettings;
import com.moneymap.model.LoginAttempt;
import com.moneymap.model.RememberMeToken;
import com.moneymap.model.User;
import com.moneymap.repository.GlobalSettingsRepository;
import com.moneymap.repository.LoginAttemptRepository;
import com.moneymap.repository.RememberMeTokenRepository;
import com.moneymap.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Login, lockout, and remember-me (Section 01 §2).
 */
@Service
public class AuthService {

    public static final String REMEMBER_ME_COOKIE = "MM_REMEMBER";
    private static final Duration REMEMBER_ME_VALIDITY = Duration.ofDays(30);

    public enum LoginStatus { SUCCESS, INVALID, DISABLED, LOCKED, PENDING_DELETION }

    public record LoginResult(LoginStatus status, User user, Instant lockedUntil) {
        static LoginResult of(LoginStatus s) { return new LoginResult(s, null, null); }
    }

    private final UserRepository users;
    private final RememberMeTokenRepository rememberMeTokens;
    private final GlobalSettingsRepository globalSettings;
    private final LoginAttemptRepository loginAttempts;
    private final BCryptPasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users,
                       RememberMeTokenRepository rememberMeTokens,
                       GlobalSettingsRepository globalSettings,
                       LoginAttemptRepository loginAttempts,
                       BCryptPasswordEncoder encoder) {
        this.users = users;
        this.rememberMeTokens = rememberMeTokens;
        this.globalSettings = globalSettings;
        this.loginAttempts = loginAttempts;
        this.encoder = encoder;
    }

    /** Resolves "username or email" per §2.1 — usernames disallow '@', so no collision is possible. */
    public Optional<User> resolveIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String id = identifier.trim();
        if (id.contains("@")) {
            Optional<User> byEmail = users.findByEmailIgnoreCase(id);
            if (byEmail.isPresent()) return byEmail;
        }
        return users.findByUsernameIgnoreCase(id);
    }

    public LoginResult login(String identifier, String password) {
        Optional<User> found = resolveIdentifier(identifier);
        if (found.isEmpty()) {
            recordFailedAttempt(identifier);
            return LoginResult.of(LoginStatus.INVALID);   // generic message — no enumeration (§2.1)
        }
        User user = found.get();

        if (user.getStatus() == User.Status.DISABLED) {
            return LoginResult.of(LoginStatus.DISABLED);
        }
        if (user.getStatus() == User.Status.PENDING_DELETION) {
            return LoginResult.of(LoginStatus.PENDING_DELETION);
        }
        // Lockout is checked BEFORE password verification (§2.1)
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            return new LoginResult(LoginStatus.LOCKED, null, user.getLockedUntil());
        }

        if (!encoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            recordFailedAttempt(identifier);
            GlobalSettings settings = globalSettings.get();
            int failures = user.getFailedLoginAttempts() + 1;
            if (failures >= settings.getMaxFailedLoginAttempts()) {
                Instant until = Instant.now().plus(Duration.ofMinutes(settings.getLockoutDurationMinutes()));
                user.setLockedUntil(until);
                user.setFailedLoginAttempts(0);   // fresh failure after expiry restarts the counter (§2.3)
                users.save(user);
                return new LoginResult(LoginStatus.LOCKED, null, until);
            }
            user.setFailedLoginAttempts(failures);
            users.save(user);
            return LoginResult.of(LoginStatus.INVALID);
        }

        // Success
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastActiveAt(Instant.now());
        users.save(user);
        // A new login invalidates any previous remember-me token (§2.2)
        rememberMeTokens.deleteByUserId(user.getId());
        return new LoginResult(LoginStatus.SUCCESS, user, null);
    }

    private void recordFailedAttempt(String identifier) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setUsernameOrEmailAttempted(identifier == null ? "" : identifier.trim());
        attempt.setTimestamp(Instant.now());
        loginAttempts.save(attempt);
    }

    // ── Remember me (§2.2) ───────────────────────────────────────────────────

    public void issueRememberMe(User user, HttpServletResponse response, boolean secureCookie) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);

        RememberMeToken token = new RememberMeToken();
        token.setUserId(user.getId());
        token.setTokenHash(encoder.encode(rawToken));   // never store the raw token
        token.setExpiresAt(Instant.now().plus(REMEMBER_ME_VALIDITY));
        RememberMeToken saved = rememberMeTokens.save(token);

        ResponseCookie cookie = ResponseCookie.from(REMEMBER_ME_COOKIE, saved.getId() + ":" + rawToken)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Strict")
                .path("/")
                .maxAge(REMEMBER_ME_VALIDITY)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * Silent re-authentication from a remember-me cookie (§2.2). Re-checks status and
     * lockout at the moment of re-auth — a token issued before a disable/lock must not grant access.
     */
    public Optional<User> silentReauthenticate(HttpServletRequest request) {
        String value = readCookie(request);
        if (value == null) return Optional.empty();
        int sep = value.indexOf(':');
        if (sep <= 0) return Optional.empty();
        String tokenId = value.substring(0, sep);
        String rawToken = value.substring(sep + 1);

        return rememberMeTokens.findAll().stream()
                .filter(t -> t.getId().equals(tokenId))
                .filter(t -> t.getExpiresAt() != null && t.getExpiresAt().isAfter(Instant.now()))
                .filter(t -> encoder.matches(rawToken, t.getTokenHash()))
                .findFirst()
                .flatMap(t -> users.findById(t.getUserId()))
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .filter(u -> u.getLockedUntil() == null || u.getLockedUntil().isBefore(Instant.now()));
    }

    public void clearRememberMe(User user, HttpServletResponse response) {
        if (user != null) {
            rememberMeTokens.deleteByUserId(user.getId());
        }
        ResponseCookie cookie = ResponseCookie.from(REMEMBER_ME_COOKIE, "")
                .httpOnly(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (REMEMBER_ME_COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
