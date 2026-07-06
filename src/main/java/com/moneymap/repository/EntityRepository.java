package com.moneymap.repository;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Generic interface-first repository (Section 00 §5.1 / Section 17 §12.1). Services depend
 * on this interface only; the JSON implementation is swappable per collection.
 */
public interface EntityRepository<T> {

    List<T> findAll();

    List<T> findWhere(Predicate<T> predicate);

    Optional<T> findById(String id);

    /** Creates (assigning id/createdAt) or updates (bumping updatedAt) — Section 17 §12.3. */
    T save(T entity);

    void deleteById(String id);

    void deleteWhere(Predicate<T> predicate);
}
