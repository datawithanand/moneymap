package com.moneymap.repository;

import com.moneymap.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Interface-first data layer (Section 00 §5.1): controllers/services depend only on this
 * interface, never on the JSON mechanics — a future PostgreSQL implementation swaps in
 * by replacing the Spring bean, with zero changes above this layer.
 */
public interface UserRepository {

    List<User> findAll();

    Optional<User> findById(String id);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    /** Creates (assigning id/createdAt) or updates (bumping updatedAt) — auto-managed fields per Section 17 §12.3. */
    User save(User user);

    void deleteById(String id);

    long count();
}
