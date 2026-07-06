package com.moneymap.web;

/** HttpSession attribute names — all session state is server-side (Section 01 §3.3). */
public final class SessionKeys {
    private SessionKeys() {}

    public static final String USER_ID = "userId";
    public static final String CSRF_TOKEN = "csrfToken";
    /** Admin read-only portfolio view context (Section 01 §6.3) — session-only, never persisted. */
    public static final String ADMIN_VIEWING_USER_ID = "adminViewingUserId";
    public static final String ADMIN_VIEW_ENTERED_AT = "adminViewEnteredAt";
}
