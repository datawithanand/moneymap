package com.moneymap.service;

import com.moneymap.model.*;
import com.moneymap.repository.Db;
import com.moneymap.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Family Vault — the deny-window emergency access state machine (Section 03/04 §3).
 * Silence = consent, but only after the owner's pre-chosen window, only for a requester the
 * owner designated in advance, and always revocable (§3.6) and fully audit-logged.
 */
@Service
public class VaultService {

    private static final Duration GRANT_DURATION = Duration.ofDays(30);

    private final Db db;
    private final UserRepository users;
    private final NotificationService notifications;
    private final AuditService audit;
    private final PermissionService permissions;

    public VaultService(Db db, UserRepository users, @Lazy NotificationService notifications,
                        AuditService audit, PermissionService permissions) {
        this.db = db;
        this.users = users;
        this.notifications = notifications;
        this.audit = audit;
        this.permissions = permissions;
    }

    // ── §3.2 Request ─────────────────────────────────────────────────────────

    public String request(User requester, String ownerId, String reason) {
        User owner = users.findById(ownerId).orElse(null);
        if (owner == null || requester.getFamilyGroupId() == null
                || !requester.getFamilyGroupId().equals(owner.getFamilyGroupId()))
            return "This member is not in your family group.";
        FamilyPermission row = permissions.permissionRow(ownerId, requester.getId()).orElse(null);
        if (row == null || !row.isDesignatedEmergencyContact())
            return "You are not designated as an emergency contact for " + owner.getFullName() + ".";
        if (reason == null || reason.trim().length() < 10)
            return "Please provide a reason (at least 10 characters).";
        if (permissions.activeGrant(ownerId, requester.getId()).isPresent())
            return "You already hold active emergency access to this member's data.";
        boolean pendingExists = !db.emergencyRequests.findWhere(r ->
                ownerId.equals(r.getOwnerId()) && requester.getId().equals(r.getRequesterId())
                        && r.getStatus() == EmergencyAccessRequest.Status.PENDING).isEmpty();
        if (pendingExists) return "A request from you to this member is already pending.";

        Instant now = Instant.now();
        EmergencyAccessRequest req = new EmergencyAccessRequest();
        req.setFamilyGroupId(requester.getFamilyGroupId());
        req.setRequesterId(requester.getId());
        req.setOwnerId(ownerId);
        req.setReason(reason.trim());
        req.setRequestedAt(now);
        req.setDenyWindowDays(owner.getEmergencyDenyWindowDays());   // snapshot (§3.2)
        req.setDenyWindowExpiresAt(now.plus(Duration.ofDays(owner.getEmergencyDenyWindowDays())));
        req.setStatus(EmergencyAccessRequest.Status.PENDING);
        EmergencyAccessRequest saved = db.emergencyRequests.save(req);

        audit.log(requester, AuditLogEntry.ActionType.EMERGENCY_ACCESS_REQUESTED, owner,
                "Emergency access requested. Reason: " + req.getReason());
        notifications.notify(ownerId, Notification.Type.EMERGENCY_ACCESS_REQUESTED, saved.getId(),
                requester.getFullName() + " requested emergency access to your data. Reason: \"" + req.getReason()
                        + "\". If you do not respond within " + req.getDenyWindowDays()
                        + " days, access will be granted automatically.");
        return null;
    }

    // ── §3.4 Approve / Deny ──────────────────────────────────────────────────

    public String approve(User owner, String requestId) {
        EmergencyAccessRequest req = pendingOwned(owner, requestId);
        if (req == null) return "Request not found or already resolved.";
        grant(req, EmergencyAccessRequest.Status.APPROVED);
        User requester = users.findById(req.getRequesterId()).orElse(null);
        audit.log(owner, AuditLogEntry.ActionType.EMERGENCY_ACCESS_APPROVED, requester, "Emergency access approved");
        notifications.notify(req.getRequesterId(), Notification.Type.EMERGENCY_ACCESS_APPROVED, req.getId(),
                owner.getFullName() + " approved your emergency access request ("
                        + req.getGrantedPermissionLevel() + ", 30 days).");
        return null;
    }

    public String deny(User owner, String requestId, String note) {
        EmergencyAccessRequest req = pendingOwned(owner, requestId);
        if (req == null) return "Request not found or already resolved.";
        req.setStatus(EmergencyAccessRequest.Status.DENIED);
        req.setRespondedAt(Instant.now());
        db.emergencyRequests.save(req);
        User requester = users.findById(req.getRequesterId()).orElse(null);
        audit.log(owner, AuditLogEntry.ActionType.EMERGENCY_ACCESS_DENIED, requester, "Emergency access denied");
        notifications.notify(req.getRequesterId(), Notification.Type.EMERGENCY_ACCESS_DENIED, req.getId(),
                owner.getFullName() + " denied your emergency access request."
                        + (note == null || note.isBlank() ? "" : " Note: " + note.trim()));
        return null;
    }

    private EmergencyAccessRequest pendingOwned(User owner, String requestId) {
        return db.emergencyRequests.findById(requestId)
                .filter(r -> owner.getId().equals(r.getOwnerId()))
                .filter(r -> r.getStatus() == EmergencyAccessRequest.Status.PENDING)
                .orElse(null);
    }

    private void grant(EmergencyAccessRequest req, EmergencyAccessRequest.Status status) {
        Instant now = Instant.now();
        FamilyPermission.Level level = permissions.permissionRow(req.getOwnerId(), req.getRequesterId())
                .map(FamilyPermission::getEmergencyAccessLevel)
                .orElse(FamilyPermission.Level.CONTACTS_ONLY);
        req.setStatus(status);
        req.setRespondedAt(now);
        req.setGrantedPermissionLevel(level);   // snapshot of the pre-configured level (§3.4)
        req.setGrantExpiresAt(now.plus(GRANT_DURATION));
        db.emergencyRequests.save(req);
    }

    // ── §3.6 Revoke ──────────────────────────────────────────────────────────

    public String revoke(User owner, String requestId) {
        EmergencyAccessRequest req = db.emergencyRequests.findById(requestId)
                .filter(r -> owner.getId().equals(r.getOwnerId()))   // only the owner — never admin (§3.6)
                .filter(r -> r.isActiveGrant(Instant.now()))
                .orElse(null);
        if (req == null) return "No active grant to revoke.";
        req.setStatus(EmergencyAccessRequest.Status.REVOKED);
        req.setRevokedAt(Instant.now());
        req.setRevokedByUserId(owner.getId());
        db.emergencyRequests.save(req);
        User requester = users.findById(req.getRequesterId()).orElse(null);
        audit.log(owner, AuditLogEntry.ActionType.EMERGENCY_ACCESS_REVOKED, requester, "Emergency access revoked");
        notifications.notify(req.getRequesterId(), Notification.Type.EMERGENCY_ACCESS_REVOKED, req.getId(),
                owner.getFullName() + " revoked your emergency access.");
        return null;
    }

    // ── §3.7 Edge-case helpers ──────────────────────────────────────────────

    /** Designation removed while a request is PENDING → auto-deny (checked at the moment of clearing). */
    public void denyPendingFromRequester(User owner, String requesterId, String systemNote) {
        for (EmergencyAccessRequest req : db.emergencyRequests.findWhere(r ->
                owner.getId().equals(r.getOwnerId()) && requesterId.equals(r.getRequesterId())
                        && r.getStatus() == EmergencyAccessRequest.Status.PENDING)) {
            req.setStatus(EmergencyAccessRequest.Status.DENIED);
            req.setRespondedAt(Instant.now());
            db.emergencyRequests.save(req);
            audit.logSystem(AuditLogEntry.ActionType.EMERGENCY_ACCESS_DENIED,
                    users.findById(requesterId).orElse(null), systemNote);
            notifications.notify(requesterId, Notification.Type.EMERGENCY_ACCESS_DENIED, req.getId(), systemNote);
        }
    }

    /** Leave/remove/dissolve force-close (§1.4/§1.5/§3.7). */
    public void forceCloseAllInvolving(String userId, String groupId) {
        Instant now = Instant.now();
        for (EmergencyAccessRequest req : db.emergencyRequests.findWhere(r ->
                groupId.equals(r.getFamilyGroupId())
                        && (userId.equals(r.getOwnerId()) || userId.equals(r.getRequesterId())))) {
            if (req.getStatus() == EmergencyAccessRequest.Status.PENDING) {
                req.setStatus(EmergencyAccessRequest.Status.DENIED);
                req.setRespondedAt(now);
                db.emergencyRequests.save(req);
            } else if (req.isActiveGrant(now)) {
                req.setStatus(EmergencyAccessRequest.Status.REVOKED);
                req.setRevokedAt(now);
                db.emergencyRequests.save(req);
            }
        }
    }

    // ── Scheduled jobs (§3.3/§3.4/§3.5) ─────────────────────────────────────

    /** Hourly: 24-hour reminders and deny-window auto-approvals in one pass. */
    @Scheduled(cron = "0 5 * * * *")
    public void vaultPass() {
        Instant now = Instant.now();
        for (EmergencyAccessRequest req : db.emergencyRequests.findWhere(r ->
                r.getStatus() == EmergencyAccessRequest.Status.PENDING)) {
            User owner = users.findById(req.getOwnerId()).orElse(null);
            User requester = users.findById(req.getRequesterId()).orElse(null);
            if (owner == null || requester == null) continue;

            if (req.getDenyWindowExpiresAt() != null && !req.getDenyWindowExpiresAt().isAfter(now)) {
                grant(req, EmergencyAccessRequest.Status.AUTO_APPROVED);
                audit.logSystem(AuditLogEntry.ActionType.EMERGENCY_ACCESS_AUTO_GRANTED, requester,
                        "Auto-approved after " + req.getDenyWindowDays() + "-day deny window elapsed (owner: "
                                + owner.getUsername() + ")");
                notifications.notify(requester.getId(), Notification.Type.EMERGENCY_ACCESS_AUTO_APPROVED, req.getId(),
                        "Your emergency access request to " + owner.getFullName() + " was automatically approved ("
                                + req.getGrantedPermissionLevel() + ", 30 days).");
                notifications.notify(owner.getId(), Notification.Type.EMERGENCY_ACCESS_AUTO_APPROVED, req.getId(),
                        "Emergency access was automatically granted to " + requester.getFullName()
                                + " because you did not respond within " + req.getDenyWindowDays() + " days.");
            } else if (!req.isReminderSent() && req.getDenyWindowExpiresAt() != null
                    && req.getDenyWindowExpiresAt().minus(Duration.ofHours(24)).isBefore(now)) {
                req.setReminderSent(true);
                db.emergencyRequests.save(req);
                notifications.notify(owner.getId(), Notification.Type.EMERGENCY_ACCESS_AUTO_APPROVAL_PENDING,
                        req.getId(), requester.getFullName() + "'s emergency access request will be automatically "
                                + "approved in about 24 hours unless you respond.");
            }
        }
    }

    /** Daily: expire 30-day grants (§3.5) and stale invitations (§1.2). */
    @Scheduled(cron = "0 20 3 * * *")
    public void expiryPass() {
        Instant now = Instant.now();
        for (EmergencyAccessRequest req : db.emergencyRequests.findWhere(r ->
                (r.getStatus() == EmergencyAccessRequest.Status.APPROVED
                        || r.getStatus() == EmergencyAccessRequest.Status.AUTO_APPROVED)
                        && r.getGrantExpiresAt() != null && !r.getGrantExpiresAt().isAfter(now))) {
            req.setStatus(EmergencyAccessRequest.Status.EXPIRED);
            db.emergencyRequests.save(req);
            notifications.notify(req.getRequesterId(), Notification.Type.EMERGENCY_ACCESS_EXPIRED, req.getId(),
                    "Your emergency access has expired.");
            notifications.notify(req.getOwnerId(), Notification.Type.EMERGENCY_ACCESS_EXPIRED, req.getId(),
                    "An emergency access grant on your data has ended.");
        }
        for (FamilyInvitation inv : db.familyInvitations.findWhere(i ->
                i.getStatus() == FamilyInvitation.Status.PENDING
                        && i.getExpiresAt() != null && !i.getExpiresAt().isAfter(now))) {
            inv.setStatus(FamilyInvitation.Status.EXPIRED);
            db.familyInvitations.save(inv);
        }
    }

    public Optional<EmergencyAccessRequest> pendingBetween(String ownerId, String requesterId) {
        return db.emergencyRequests.findWhere(r -> ownerId.equals(r.getOwnerId())
                && requesterId.equals(r.getRequesterId())
                && r.getStatus() == EmergencyAccessRequest.Status.PENDING).stream().findFirst();
    }
}
