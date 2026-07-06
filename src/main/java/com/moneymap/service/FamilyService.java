package com.moneymap.service;

import com.moneymap.model.*;
import com.moneymap.repository.Db;
import com.moneymap.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Family group lifecycle & permission management (Section 03/04 §1–§2). */
@Service
public class FamilyService {

    private final Db db;
    private final UserRepository users;
    private final NotificationService notifications;
    private final VaultService vault;

    public FamilyService(Db db, UserRepository users, NotificationService notifications, VaultService vault) {
        this.db = db;
        this.users = users;
        this.notifications = notifications;
        this.vault = vault;
    }

    public List<User> activeMembers(String groupId) {
        return users.findAll().stream()
                .filter(u -> groupId.equals(u.getFamilyGroupId()))
                .filter(u -> u.getStatus() == User.Status.ACTIVE)
                .toList();
    }

    // ── §1.1 Create ──────────────────────────────────────────────────────────

    public String createGroup(User creator, String groupName) {
        if (creator.getFamilyGroupId() != null)
            return "You're already part of a family group. Leave your current group before creating another.";
        if (groupName == null || groupName.trim().length() < 3 || groupName.trim().length() > 60)
            return "Group name must be 3–60 characters.";
        FamilyGroup group = new FamilyGroup();
        group.setGroupName(groupName.trim());
        group.setAdminUserId(creator.getId());
        FamilyGroup saved = db.familyGroups.save(group);
        joinGroup(creator, saved.getId(), true);
        return null;
    }

    private void joinGroup(User user, String groupId, boolean asAdmin) {
        user.setFamilyGroupId(groupId);
        user.setFamilyAdmin(asAdmin);
        // Role derivation (§1.5): REGULAR → FAMILY_MEMBER; ADMIN keeps ADMIN throughout.
        if (user.getRole() == User.Role.REGULAR) user.setRole(User.Role.FAMILY_MEMBER);
        users.save(user);
    }

    // ── §1.2 / §1.3 Invitations ──────────────────────────────────────────────

    public String invite(User inviter, String identifier) {
        if (inviter.getFamilyGroupId() == null) return "You are not in a family group.";
        String id = identifier == null ? "" : identifier.trim();
        if (id.isEmpty()) return "Enter a username or email address.";

        User invitee = null;
        if (id.contains("@")) {
            invitee = users.findByEmailIgnoreCase(id).orElse(null);
        }
        if (invitee == null) invitee = users.findByUsernameIgnoreCase(id).orElse(null);

        FamilyInvitation inv = new FamilyInvitation();
        inv.setFamilyGroupId(inviter.getFamilyGroupId());
        inv.setInvitedByUserId(inviter.getId());
        inv.setExpiresAt(Instant.now().plus(Duration.ofDays(14)));

        if (invitee != null) {
            if (invitee.getFamilyGroupId() != null) return "This user is already part of a family group.";
            boolean pendingExists = !db.familyInvitations.findWhere(i ->
                    inviter.getFamilyGroupId().equals(i.getFamilyGroupId())
                            && invitee(i, id) && i.getStatus() == FamilyInvitation.Status.PENDING).isEmpty();
            if (pendingExists) return "An invitation to this person is already pending.";
            inv.setInviteeUserId(invitee.getId());
            inv.setInviteeUsername(invitee.getUsername());
            db.familyInvitations.save(inv);
            String groupName = groupName(inviter.getFamilyGroupId());
            notifications.notify(invitee.getId(), Notification.Type.FAMILY_INVITATION_RECEIVED, inv.getId(),
                    inviter.getFullName() + " invited you to join the family group \"" + groupName + "\".");
        } else if (id.contains("@")) {
            // Unregistered email — attaches automatically if that email registers later (§1.2)
            inv.setInviteeEmail(id.toLowerCase());
            db.familyInvitations.save(inv);
        } else {
            return "No MoneyMap user found with that username. To invite by email, enter an email address.";
        }
        return null;
    }

    private boolean invitee(FamilyInvitation i, String identifier) {
        return identifier.equalsIgnoreCase(i.getInviteeUsername())
                || identifier.equalsIgnoreCase(i.getInviteeEmail());
    }

    /** Surfaces a pending email invitation when a new user registers with a matching email (§1.2). */
    public void attachEmailInvitations(User newUser) {
        for (FamilyInvitation inv : db.familyInvitations.findWhere(i ->
                i.getStatus() == FamilyInvitation.Status.PENDING
                        && i.getInviteeUserId() == null
                        && newUser.getEmail().equalsIgnoreCase(i.getInviteeEmail()))) {
            inv.setInviteeUserId(newUser.getId());
            inv.setInviteeUsername(newUser.getUsername());
            db.familyInvitations.save(inv);
            notifications.notify(newUser.getId(), Notification.Type.FAMILY_INVITATION_RECEIVED, inv.getId(),
                    "You have a pending invitation to join the family group \"" + groupName(inv.getFamilyGroupId()) + "\".");
        }
    }

    public List<FamilyInvitation> pendingInvitationsFor(User user) {
        return db.familyInvitations.findWhere(i -> i.getStatus() == FamilyInvitation.Status.PENDING
                && user.getId().equals(i.getInviteeUserId()));
    }

    public List<FamilyInvitation> pendingInvitationsOfGroup(String groupId) {
        return db.familyInvitations.findWhere(i -> i.getStatus() == FamilyInvitation.Status.PENDING
                && groupId.equals(i.getFamilyGroupId()));
    }

    public String accept(User invitee, String invitationId) {
        FamilyInvitation inv = db.familyInvitations.findById(invitationId)
                .filter(i -> invitee.getId().equals(i.getInviteeUserId()))
                .filter(i -> i.getStatus() == FamilyInvitation.Status.PENDING)
                .orElse(null);
        if (inv == null) return "Invitation not found or no longer valid.";
        if (invitee.getFamilyGroupId() != null)
            return "You're already part of a family group. Leave your current group before joining another.";
        if (inv.getExpiresAt() != null && inv.getExpiresAt().isBefore(Instant.now())) {
            inv.setStatus(FamilyInvitation.Status.EXPIRED);
            db.familyInvitations.save(inv);
            return "This invitation has expired — ask to be re-invited.";
        }
        inv.setStatus(FamilyInvitation.Status.ACCEPTED);
        inv.setRespondedAt(Instant.now());
        db.familyInvitations.save(inv);
        List<User> existing = activeMembers(inv.getFamilyGroupId());
        joinGroup(invitee, inv.getFamilyGroupId(), false);
        // Every permission defaults to NO_ACCESS — joining never grants any sharing (§1.3).
        for (User member : existing) {
            notifications.notify(member.getId(), Notification.Type.FAMILY_MEMBER_JOINED, inv.getFamilyGroupId(),
                    invitee.getFullName() + " joined the family group.");
        }
        return null;
    }

    public void decline(User invitee, String invitationId) {
        db.familyInvitations.findById(invitationId)
                .filter(i -> invitee.getId().equals(i.getInviteeUserId()))
                .filter(i -> i.getStatus() == FamilyInvitation.Status.PENDING)
                .ifPresent(inv -> {
                    inv.setStatus(FamilyInvitation.Status.DECLINED);
                    inv.setRespondedAt(Instant.now());
                    db.familyInvitations.save(inv);
                    notifications.notify(inv.getInvitedByUserId(), Notification.Type.FAMILY_INVITATION_DECLINED,
                            inv.getId(), invitee.getFullName() + " declined your family group invitation.");
                });
    }

    public void cancelInvitation(User actor, String invitationId) {
        db.familyInvitations.findById(invitationId)
                .filter(i -> i.getStatus() == FamilyInvitation.Status.PENDING)
                .filter(i -> actor.getId().equals(i.getInvitedByUserId()) || isFamilyAdminOf(actor, i.getFamilyGroupId()))
                .ifPresent(inv -> {
                    inv.setStatus(FamilyInvitation.Status.CANCELLED);
                    db.familyInvitations.save(inv);
                });
    }

    // ── §1.4 / §1.5 Leave, remove, dissolve, transfer ───────────────────────

    public boolean isFamilyAdminOf(User user, String groupId) {
        return db.familyGroups.findById(groupId)
                .map(g -> user.getId().equals(g.getAdminUserId())).orElse(false);
    }

    /** Shared cleanup for leave/remove/dissolve: end all sharing and vault state for this user in this group. */
    private void detachFromGroup(User user, String groupId, Notification.Type type, String message) {
        db.familyPermissions.deleteWhere(p -> groupId.equals(p.getFamilyGroupId())
                && (user.getId().equals(p.getOwnerUserId()) || user.getId().equals(p.getViewerUserId())));
        vault.forceCloseAllInvolving(user.getId(), groupId);
        user.setFamilyGroupId(null);
        user.setFamilyAdmin(false);
        if (user.getRole() == User.Role.FAMILY_MEMBER) user.setRole(User.Role.REGULAR);
        users.save(user);
        if (type != null) notifications.notify(user.getId(), type, groupId, message);
    }

    public String leave(User member) {
        String groupId = member.getFamilyGroupId();
        if (groupId == null) return "You are not in a family group.";
        if (isFamilyAdminOf(member, groupId))
            return "The Family Admin cannot leave — transfer the admin role or dissolve the group first.";
        String name = member.getFullName();
        detachFromGroup(member, groupId, null, null);
        for (User remaining : activeMembers(groupId)) {
            notifications.notify(remaining.getId(), Notification.Type.FAMILY_MEMBER_LEFT, groupId,
                    name + " left the family group.");
        }
        return null;
    }

    public String removeMember(User admin, String targetUserId) {
        String groupId = admin.getFamilyGroupId();
        if (groupId == null || !isFamilyAdminOf(admin, groupId)) return "Only the Family Admin can remove members.";
        if (admin.getId().equals(targetUserId)) return "Use dissolve or transfer instead of removing yourself.";
        User target = users.findById(targetUserId)
                .filter(u -> groupId.equals(u.getFamilyGroupId())).orElse(null);
        if (target == null) return "Member not found in your group.";
        String groupName = groupName(groupId);
        detachFromGroup(target, groupId, Notification.Type.FAMILY_MEMBER_REMOVED,
                "You were removed from the family group \"" + groupName + "\".");
        for (User remaining : activeMembers(groupId)) {
            notifications.notify(remaining.getId(), Notification.Type.FAMILY_MEMBER_REMOVED, groupId,
                    target.getFullName() + " was removed from the family group.");
        }
        return null;
    }

    public String dissolve(User admin, String typedName) {
        String groupId = admin.getFamilyGroupId();
        if (groupId == null || !isFamilyAdminOf(admin, groupId)) return "Only the Family Admin can dissolve the group.";
        FamilyGroup group = db.familyGroups.findById(groupId).orElse(null);
        if (group == null) return "Group not found.";
        if (!group.getGroupName().equals(typedName == null ? "" : typedName.trim()))
            return "Group name confirmation does not match.";
        List<User> members = activeMembers(groupId);
        for (User member : members) {
            detachFromGroup(member, groupId, Notification.Type.FAMILY_GROUP_DISSOLVED,
                    "The family group \"" + group.getGroupName() + "\" was dissolved.");
        }
        db.familyInvitations.deleteWhere(i -> groupId.equals(i.getFamilyGroupId())
                && i.getStatus() == FamilyInvitation.Status.PENDING);
        db.familyGroups.deleteById(groupId);
        return null;
    }

    public String transferAdmin(User admin, String newAdminUserId) {
        String groupId = admin.getFamilyGroupId();
        if (groupId == null || !isFamilyAdminOf(admin, groupId)) return "Only the Family Admin can transfer the role.";
        User newAdmin = users.findById(newAdminUserId)
                .filter(u -> groupId.equals(u.getFamilyGroupId()))
                .filter(u -> u.getStatus() == User.Status.ACTIVE).orElse(null);
        if (newAdmin == null) return "Selected member not found in your group.";
        FamilyGroup group = db.familyGroups.findById(groupId).orElseThrow();
        group.setAdminUserId(newAdmin.getId());
        db.familyGroups.save(group);
        admin.setFamilyAdmin(false);
        users.save(admin);
        newAdmin.setFamilyAdmin(true);
        users.save(newAdmin);
        notifications.notify(newAdmin.getId(), Notification.Type.PERMISSION_LEVEL_CHANGED, groupId,
                "You are now the Family Admin of \"" + group.getGroupName() + "\".");
        return null;
    }

    // ── §2 Permission matrix ─────────────────────────────────────────────────

    public String updatePermission(User owner, String viewerUserId, FamilyPermission.Level standing,
                                   boolean designated, FamilyPermission.Level emergencyLevel) {
        String groupId = owner.getFamilyGroupId();
        if (groupId == null) return "You are not in a family group.";
        User viewer = users.findById(viewerUserId)
                .filter(u -> groupId.equals(u.getFamilyGroupId())).orElse(null);
        if (viewer == null) return "Member not found in your group.";
        if (designated && (emergencyLevel == null || emergencyLevel == FamilyPermission.Level.NO_ACCESS))
            return "An emergency contact must have an emergency access level.";

        FamilyPermission row = db.familyPermissions.findWhere(p ->
                owner.getId().equals(p.getOwnerUserId()) && viewerUserId.equals(p.getViewerUserId()))
                .stream().findFirst().orElseGet(FamilyPermission::new);
        boolean wasDesignated = row.isDesignatedEmergencyContact();
        boolean levelChanged = row.getStandingPermissionLevel() != standing
                || row.isDesignatedEmergencyContact() != designated
                || row.getEmergencyAccessLevel() != emergencyLevel;
        row.setFamilyGroupId(groupId);
        row.setOwnerUserId(owner.getId());
        row.setViewerUserId(viewerUserId);
        row.setStandingPermissionLevel(standing == null ? FamilyPermission.Level.NO_ACCESS : standing);
        row.setDesignatedEmergencyContact(designated);
        row.setEmergencyAccessLevel(designated ? emergencyLevel : null);

        // Sparse storage (§2.1): NO_ACCESS + not designated = delete the row
        if (row.getStandingPermissionLevel() == FamilyPermission.Level.NO_ACCESS && !designated) {
            if (row.getId() != null) db.familyPermissions.deleteById(row.getId());
        } else {
            db.familyPermissions.save(row);
        }

        // Edge case §3.7: removing designation auto-denies any PENDING request from that member
        if (wasDesignated && !designated) {
            vault.denyPendingFromRequester(owner, viewerUserId,
                    "Automatically denied — the owner removed this member's emergency contact designation.");
        }
        if (levelChanged) {
            notifications.notify(viewerUserId, Notification.Type.PERMISSION_LEVEL_CHANGED, owner.getId(),
                    owner.getFullName() + " updated what they share with you.");
        }
        return null;
    }

    public String groupName(String groupId) {
        return db.familyGroups.findById(groupId).map(FamilyGroup::getGroupName).orElse("(family group)");
    }
}
