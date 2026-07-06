package com.moneymap.service;

import com.moneymap.model.EmergencyAccessRequest;
import com.moneymap.model.FamilyPermission;
import com.moneymap.repository.Db;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

/**
 * Permission resolution (Section 03/04 §2, §3.5, §4.4). Every check keys strictly on
 * (recordOwnerId, currentViewerId) — access is directional, never symmetric, never transitive.
 */
@Service
public class PermissionService {

    private final Db db;

    public PermissionService(Db db) {
        this.db = db;
    }

    public Optional<FamilyPermission> permissionRow(String ownerId, String viewerId) {
        return db.familyPermissions.findWhere(p ->
                ownerId.equals(p.getOwnerUserId()) && viewerId.equals(p.getViewerUserId())).stream().findFirst();
    }

    /** Standing level; a missing row is exactly NO_ACCESS (§2.1 sparse storage). */
    public FamilyPermission.Level standingLevel(String ownerId, String viewerId) {
        return permissionRow(ownerId, viewerId)
                .map(FamilyPermission::getStandingPermissionLevel)
                .orElse(FamilyPermission.Level.NO_ACCESS);
    }

    /** The currently-active emergency grant, if any (§3.5). */
    public Optional<EmergencyAccessRequest> activeGrant(String ownerId, String viewerId) {
        Instant now = Instant.now();
        return db.emergencyRequests.findWhere(r ->
                ownerId.equals(r.getOwnerId()) && viewerId.equals(r.getRequesterId())
                        && r.isActiveGrant(now)).stream().findFirst();
    }

    /**
     * Effective level = the higher of the standing level and any active emergency grant —
     * emergency-granted access behaves identically to standing access at the same level (§3.5).
     */
    public FamilyPermission.Level effectiveLevel(String ownerId, String viewerId) {
        FamilyPermission.Level standing = standingLevel(ownerId, viewerId);
        FamilyPermission.Level granted = activeGrant(ownerId, viewerId)
                .map(EmergencyAccessRequest::getGrantedPermissionLevel)
                .orElse(FamilyPermission.Level.NO_ACCESS);
        return rank(standing) >= rank(granted) ? standing : granted;
    }

    public static int rank(FamilyPermission.Level level) {
        if (level == null) return 0;
        return switch (level) {
            case NO_ACCESS -> 0;
            case SUMMARY_ONLY -> 1;
            case CONTACTS_ONLY -> 2;
            case FULL_ACCESS -> 3;
        };
    }

    public boolean atLeast(FamilyPermission.Level actual, FamilyPermission.Level required) {
        return rank(actual) >= rank(required);
    }
}
