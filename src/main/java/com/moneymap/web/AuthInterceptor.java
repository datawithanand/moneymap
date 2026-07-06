package com.moneymap.web;

import com.moneymap.model.User;
import com.moneymap.repository.GlobalSettingsRepository;
import com.moneymap.repository.UserRepository;
import com.moneymap.service.AuthService;
import com.moneymap.service.SessionRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Custom session-validation filter/interceptor (Section 00 §5.2, Section 01 §3):
 * session check → remember-me silent re-auth → status re-check → single-session
 * enforcement → CSRF (Section 16) → forced-password-change gate → onboarding gate
 * → admin route guard → admin read-only view write-block (§6.3).
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final Duration ADMIN_VIEW_LIMIT = Duration.ofMinutes(15);

    private final UserRepository users;
    private final GlobalSettingsRepository globalSettings;
    private final AuthService authService;
    private final SessionRegistry sessionRegistry;
    private final SecureRandom random = new SecureRandom();

    public AuthInterceptor(UserRepository users, GlobalSettingsRepository globalSettings,
                           AuthService authService, SessionRegistry sessionRegistry) {
        this.users = users;
        this.globalSettings = globalSettings;
        this.authService = authService;
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String path = request.getRequestURI();

        if (isPublic(path)) {
            // CSRF still applies to public POSTs (login/register) — Section 16.
            if (isWrite(request) && !csrfValid(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
                return false;
            }
            return true;
        }

        HttpSession session = request.getSession(false);
        User user = null;

        if (session != null && session.getAttribute(SessionKeys.USER_ID) instanceof String userId) {
            user = users.findById(userId).orElse(null);
        }

        // Remember-me silent re-authentication (§2.2) takes precedence over the expired-session message (§3.1)
        if (user == null) {
            Optional<User> remembered = authService.silentReauthenticate(request);
            if (remembered.isPresent()) {
                user = remembered.get();
                session = establishSession(request, user);
            }
        }

        if (user == null) {
            response.sendRedirect("/login?expired=1");
            return false;
        }

        // Status re-check on every request — disable/deletion takes effect immediately (§5.5, §6.4)
        if (user.getStatus() != User.Status.ACTIVE) {
            session.invalidate();
            sessionRegistry.deregister(user.getId(), session.getId());
            response.sendRedirect("/login?disabled=1");
            return false;
        }

        // Single active session per user (§3.2). After a JVM restart the registry is empty —
        // re-adopt this session rather than bouncing the user.
        if (!sessionRegistry.adoptIfAbsent(user.getId(), session)) {
            try { session.invalidate(); } catch (IllegalStateException ignored) {}
            response.sendRedirect("/login?elsewhere=1");
            return false;
        }

        // CSRF on every state-changing request (Section 16)
        if (isWrite(request) && !csrfValid(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid CSRF token");
            return false;
        }

        // Forced password change gate (§1.3): everything except the change screen and logout redirects back
        if (user.isMustChangePassword() && !path.startsWith("/password/force") && !path.equals("/logout")) {
            response.sendRedirect("/password/force");
            return false;
        }

        // Setup wizard gate (§1.2)
        if (!user.isMustChangePassword() && !user.isOnboardingCompleted()
                && !path.startsWith("/setup") && !path.equals("/logout")) {
            response.sendRedirect("/setup");
            return false;
        }

        // Admin routes (§6.1)
        if (path.startsWith("/admin") && user.getRole() != User.Role.ADMIN) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        // Admin read-only view mode (§6.3): 15-minute expiry; writes blocked at this layer as defense in depth
        Object viewing = session.getAttribute(SessionKeys.ADMIN_VIEWING_USER_ID);
        if (viewing != null) {
            Instant entered = (Instant) session.getAttribute(SessionKeys.ADMIN_VIEW_ENTERED_AT);
            if (entered == null || entered.plus(ADMIN_VIEW_LIMIT).isBefore(Instant.now())) {
                session.removeAttribute(SessionKeys.ADMIN_VIEWING_USER_ID);
                session.removeAttribute(SessionKeys.ADMIN_VIEW_ENTERED_AT);
            } else if (isWrite(request) && !path.equals("/admin/view/exit")) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Read-only administrator view — write actions are not permitted.");
                return false;
            }
        }

        request.setAttribute("currentUser", user);
        return true;
    }

    /** Creates the authenticated session (used by login and remember-me re-auth). */
    public HttpSession establishSession(HttpServletRequest request, User user) {
        HttpSession old = request.getSession(false);
        String csrf = null;
        if (old != null) {
            csrf = (String) old.getAttribute(SessionKeys.CSRF_TOKEN);
            old.invalidate();   // session fixation protection
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        session.setAttribute(SessionKeys.CSRF_TOKEN, csrf != null ? csrf : newToken());
        // Timeout from GlobalSettings at session-creation time (§3.1)
        session.setMaxInactiveInterval(globalSettings.get().getSessionTimeoutMinutes() * 60);
        sessionRegistry.register(user.getId(), session);
        return session;
    }

    private String newToken() {
        byte[] raw = new byte[24];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private boolean isWrite(HttpServletRequest request) {
        String m = request.getMethod();
        return m.equals("POST") || m.equals("PUT") || m.equals("DELETE") || m.equals("PATCH");
    }

    private boolean csrfValid(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return false;
        String expected = (String) session.getAttribute(SessionKeys.CSRF_TOKEN);
        String actual = request.getParameter("_csrf");
        return expected != null && expected.equals(actual);
    }

    private boolean isPublic(String path) {
        return path.equals("/") || path.equals("/login") || path.equals("/register")
                || path.startsWith("/css/") || path.startsWith("/js/")
                || path.equals("/favicon.ico")
                || path.startsWith("/actuator/health")
                || path.equals("/error");
    }
}
