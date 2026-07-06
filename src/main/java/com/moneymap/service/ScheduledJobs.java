package com.moneymap.service;

import com.moneymap.model.AuditLogEntry;
import com.moneymap.model.User;
import com.moneymap.model.asset.OwnedRecord;
import com.moneymap.module.ModuleDef;
import com.moneymap.module.ModuleRegistry;
import com.moneymap.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Daily maintenance jobs. Cron runs in the instance time zone (TZ env var, default
 * Asia/Kolkata — Section 15 §5 / Section 16 Reliability).
 */
@Service
public class ScheduledJobs {

    private static final Logger log = LoggerFactory.getLogger(ScheduledJobs.class);

    private final UserRepository users;
    private final HouseholdMemberRepository householdMembers;
    private final RememberMeTokenRepository rememberMeTokens;
    private final LoginAttemptRepository loginAttempts;
    private final AuditService audit;
    private final ModuleRegistry moduleRegistry;
    private final Db db;
    private final Path uploadsDir;

    public ScheduledJobs(UserRepository users,
                         HouseholdMemberRepository householdMembers,
                         RememberMeTokenRepository rememberMeTokens,
                         LoginAttemptRepository loginAttempts,
                         AuditService audit,
                         ModuleRegistry moduleRegistry,
                         Db db,
                         @Value("${moneymap.data-dir}") String dataDir) {
        this.users = users;
        this.householdMembers = householdMembers;
        this.rememberMeTokens = rememberMeTokens;
        this.loginAttempts = loginAttempts;
        this.audit = audit;
        this.moduleRegistry = moduleRegistry;
        this.db = db;
        this.uploadsDir = Paths.get(dataDir, "uploads", "profile-photos");
    }

    /**
     * Daily purge of accounts past their 30-day deletion grace period (Section 01 §5.5).
     * Deletes the User record, their household member labels, remember-me tokens and
     * uploaded files, then writes an ACCOUNT_PURGED audit entry attributed to SYSTEM.
     *
     * NOTE for later increments: as financial modules (Sections 05–11A) are implemented,
     * their collections must be added to this purge (every record whose ownerId matches),
     * along with Family Permission records where the user was owner or viewer (Section 03/04).
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeDeletedAccounts() {
        Instant now = Instant.now();
        List<User> due = users.findAll().stream()
                .filter(u -> u.getStatus() == User.Status.PENDING_DELETION)
                .filter(u -> u.getScheduledPurgeAt() != null && !u.getScheduledPurgeAt().isAfter(now))
                .toList();
        for (User user : due) {
            String uid = user.getId();
            // Every financial record whose ownerId is this user's id (all modules, Sections 05–11A)
            for (ModuleDef<?> def : moduleRegistry.all()) {
                def.repo.deleteWhere(r -> uid.equals(((OwnedRecord) r).getOwnerId()));
            }
            db.pfEmployerRecords.deleteWhere(r -> uid.equals(r.getOwnerId()));
            db.netWorthSnapshots.deleteWhere(s -> uid.equals(s.getOwnerId()));
            db.allocationTargets.deleteWhere(t -> uid.equals(t.getOwnerId()));
            db.notifications.deleteWhere(n -> uid.equals(n.getUserId()));
            // Family Permission records where this user was owner or viewer (Section 03/04)
            db.familyPermissions.deleteWhere(p -> uid.equals(p.getOwnerUserId()) || uid.equals(p.getViewerUserId()));
            db.emergencyRequests.deleteWhere(r -> uid.equals(r.getOwnerId()) || uid.equals(r.getRequesterId()));
            db.familyInvitations.deleteWhere(i -> uid.equals(i.getInviteeUserId()) || uid.equals(i.getInvitedByUserId()));
            householdMembers.deleteByOwnerId(uid);
            rememberMeTokens.deleteByUserId(uid);
            deleteUploads(user);
            users.deleteById(uid);
            audit.logSystem(AuditLogEntry.ActionType.ACCOUNT_PURGED, user,
                    "Account and all owned data permanently purged after 30-day grace period");
            log.info("Purged account {} after deletion grace period", user.getUsername());
        }
    }

    private void deleteUploads(User user) {
        if (user.getProfilePhotoPath() == null) return;
        try {
            Files.deleteIfExists(uploadsDir.resolve(Paths.get(user.getProfilePhotoPath()).getFileName()));
        } catch (IOException e) {
            log.warn("Could not delete profile photo for purged user {}", user.getUsername(), e);
        }
    }

    /** Rolling 30-day retention for the failed-login log (Section 02); also prunes expired remember-me tokens. */
    @Scheduled(cron = "0 15 3 * * *")
    public void pruneOperationalLogs() {
        loginAttempts.deleteOlderThan(Instant.now().minus(Duration.ofDays(30)));
        rememberMeTokens.deleteExpired(Instant.now());
    }
}
