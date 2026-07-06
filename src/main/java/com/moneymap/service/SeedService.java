package com.moneymap.service;

import com.moneymap.model.User;
import com.moneymap.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * First-run admin bootstrap (Section 01 §1.3): if users.json is empty, seed exactly one
 * admin/admin account (BCrypt-hashed from the moment of creation, never logged in plaintext)
 * with a mandatory forced password change. Strictly a one-time, empty-collection bootstrap.
 */
@Service
public class SeedService {

    private static final Logger log = LoggerFactory.getLogger(SeedService.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final BCryptPasswordEncoder encoder;

    public SeedService(UserRepository userRepository, UserService userService, BCryptPasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.encoder = encoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnFirstRun() {
        if (userRepository.count() > 0) {
            return;
        }
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@localhost");
        admin.setFullName("Administrator");
        admin.setPasswordHash(encoder.encode("admin"));  // hashed before storage; plaintext never logged
        admin.setRole(User.Role.ADMIN);
        admin.setStatus(User.Status.ACTIVE);
        admin.setMustChangePassword(true);
        admin.setOnboardingCompleted(false);
        User saved = userRepository.save(admin);
        userService.seedSelfHouseholdMember(saved.getId());
        log.info("[SeedService] Empty users collection detected — seeded default admin account "
                + "(username 'admin', forced password change on first login).");
    }
}
