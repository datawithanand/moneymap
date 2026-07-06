package com.moneymap.repository.json;

import com.moneymap.model.RememberMeToken;
import com.moneymap.repository.JsonCollectionStore;
import com.moneymap.repository.RememberMeTokenRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class JsonRememberMeTokenRepository implements RememberMeTokenRepository {

    private static final String COLLECTION = "remember_me_tokens";

    private final JsonCollectionStore store;

    public JsonRememberMeTokenRepository(JsonCollectionStore store) {
        this.store = store;
    }

    @Override
    public List<RememberMeToken> findAll() {
        return store.readAll(COLLECTION, RememberMeToken.class);
    }

    @Override
    public List<RememberMeToken> findByUserId(String userId) {
        return findAll().stream().filter(t -> t.getUserId().equals(userId)).toList();
    }

    @Override
    public RememberMeToken save(RememberMeToken token) {
        return store.mutate(COLLECTION, RememberMeToken.class, list -> {
            if (token.getId() == null) {
                token.setId(UUID.randomUUID().toString());
                token.setCreatedAt(Instant.now());
            }
            list.removeIf(t -> t.getId().equals(token.getId()));
            list.add(token);
            return token;
        });
    }

    @Override
    public void deleteByUserId(String userId) {
        store.mutate(COLLECTION, RememberMeToken.class, list -> {
            list.removeIf(t -> t.getUserId().equals(userId));
            return null;
        });
    }

    @Override
    public void deleteExpired(Instant now) {
        store.mutate(COLLECTION, RememberMeToken.class, list -> {
            list.removeIf(t -> t.getExpiresAt() != null && t.getExpiresAt().isBefore(now));
            return null;
        });
    }
}
