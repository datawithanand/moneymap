package com.moneymap.repository.json;

import com.moneymap.model.LoginAttempt;
import com.moneymap.repository.JsonCollectionStore;
import com.moneymap.repository.LoginAttemptRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JsonLoginAttemptRepository implements LoginAttemptRepository {

    private static final String COLLECTION = "login_attempt_log";

    private final JsonCollectionStore store;

    public JsonLoginAttemptRepository(JsonCollectionStore store) {
        this.store = store;
    }

    @Override
    public LoginAttempt save(LoginAttempt attempt) {
        return store.mutate(COLLECTION, LoginAttempt.class, list -> {
            attempt.setId(UUID.randomUUID().toString());
            if (attempt.getTimestamp() == null) {
                attempt.setTimestamp(Instant.now());
            }
            list.add(attempt);
            return attempt;
        });
    }

    @Override
    public List<LoginAttempt> findSince(Instant since) {
        return store.readAll(COLLECTION, LoginAttempt.class).stream()
                .filter(a -> a.getTimestamp() != null && a.getTimestamp().isAfter(since))
                .toList();
    }

    @Override
    public void deleteOlderThan(Instant cutoff) {
        store.mutate(COLLECTION, LoginAttempt.class, list -> {
            list.removeIf(a -> a.getTimestamp() == null || a.getTimestamp().isBefore(cutoff));
            return null;
        });
    }
}
