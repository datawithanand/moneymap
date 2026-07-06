package com.moneymap.repository.json;

import com.moneymap.model.User;
import com.moneymap.repository.JsonCollectionStore;
import com.moneymap.repository.UserRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JsonUserRepository implements UserRepository {

    static final String COLLECTION = "users";

    private final JsonCollectionStore store;

    public JsonUserRepository(JsonCollectionStore store) {
        this.store = store;
    }

    @Override
    public List<User> findAll() {
        return store.readAll(COLLECTION, User.class);
    }

    @Override
    public Optional<User> findById(String id) {
        return findAll().stream().filter(u -> u.getId().equals(id)).findFirst();
    }

    @Override
    public Optional<User> findByUsernameIgnoreCase(String username) {
        if (username == null) return Optional.empty();
        return findAll().stream()
                .filter(u -> username.equalsIgnoreCase(u.getUsername()))
                .findFirst();
    }

    @Override
    public Optional<User> findByEmailIgnoreCase(String email) {
        if (email == null) return Optional.empty();
        return findAll().stream()
                .filter(u -> u.getEmail() != null && email.equalsIgnoreCase(u.getEmail()))
                .findFirst();
    }

    @Override
    public User save(User user) {
        return store.mutate(COLLECTION, User.class, list -> {
            Instant now = Instant.now();
            if (user.getId() == null) {
                user.setId(UUID.randomUUID().toString());
                user.setCreatedAt(now);
            }
            user.setUpdatedAt(now);
            list.removeIf(u -> u.getId().equals(user.getId()));
            list.add(user);
            return user;
        });
    }

    @Override
    public void deleteById(String id) {
        store.mutate(COLLECTION, User.class, list -> {
            list.removeIf(u -> u.getId().equals(id));
            return null;
        });
    }

    @Override
    public long count() {
        return findAll().size();
    }
}
