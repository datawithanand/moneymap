package com.moneymap.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory single-active-session registry (Section 01 §3.2).
 * Valid for the single-JVM, non-clustered deployment model — cleared on restart by design.
 */
@Component
public class SessionRegistry {

    private final ConcurrentHashMap<String, HttpSession> sessions = new ConcurrentHashMap<>();

    /** Registers the new session and explicitly invalidates any prior one for this user. */
    public void register(String userId, HttpSession session) {
        HttpSession previous = sessions.put(userId, session);
        if (previous != null && !previous.getId().equals(session.getId())) {
            try {
                previous.invalidate();
            } catch (IllegalStateException ignored) {
                // already invalidated
            }
        }
    }

    /** True if this session is the one currently registered for the user. */
    public boolean isCurrent(String userId, HttpSession session) {
        HttpSession current = sessions.get(userId);
        return current != null && current.getId().equals(session.getId());
    }

    /**
     * Registers this session only if no session is currently registered for the user —
     * used to re-adopt a still-valid container session after a JVM restart cleared the
     * in-memory registry (Section 01 §3.2's restart note). Returns true if this session
     * is now the registered one.
     */
    public boolean adoptIfAbsent(String userId, HttpSession session) {
        HttpSession current = sessions.putIfAbsent(userId, session);
        return current == null || current.getId().equals(session.getId());
    }

    public void deregister(String userId, String sessionId) {
        HttpSession current = sessions.get(userId);
        if (current != null && current.getId().equals(sessionId)) {
            sessions.remove(userId);
        }
    }

    /** Immediately ends a user's active session (admin disable, password reset, deletion — §4.2/§5.5/§6.4). */
    public void invalidateUser(String userId) {
        HttpSession session = sessions.remove(userId);
        if (session != null) {
            try {
                session.invalidate();
            } catch (IllegalStateException ignored) {
            }
        }
    }
}
