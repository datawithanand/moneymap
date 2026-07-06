package com.moneymap.repository.json;

import com.moneymap.model.InviteToken;
import com.moneymap.repository.InviteTokenRepository;
import com.moneymap.repository.JsonCollectionStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JsonInviteTokenRepository implements InviteTokenRepository {

    private static final String COLLECTION = "invite_tokens";

    private final JsonCollectionStore store;

    public JsonInviteTokenRepository(JsonCollectionStore store) {
        this.store = store;
    }

    @Override
    public List<InviteToken> findAll() {
        return store.readAll(COLLECTION, InviteToken.class);
    }

    @Override
    public Optional<InviteToken> findByToken(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return findAll().stream().filter(t -> token.equals(t.getToken())).findFirst();
    }

    @Override
    public InviteToken save(InviteToken token) {
        return store.mutate(COLLECTION, InviteToken.class, list -> {
            if (token.getId() == null) {
                token.setId(UUID.randomUUID().toString());
                token.setCreatedAt(Instant.now());
            }
            list.removeIf(t -> t.getId().equals(token.getId()));
            list.add(token);
            return token;
        });
    }
}
