package com.moneymap.repository.json;

import com.moneymap.repository.EntityRepository;
import com.moneymap.repository.JsonCollectionStore;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Generic JSON-file repository. Auto-manages id/createdAt/updatedAt via bean properties
 * (Section 17 §12.3); all writes go through the store's per-collection lock + atomic rename.
 */
public class JsonEntityRepository<T> implements EntityRepository<T> {

    private final JsonCollectionStore store;
    private final String collection;
    private final Class<T> type;

    public JsonEntityRepository(JsonCollectionStore store, String collection, Class<T> type) {
        this.store = store;
        this.collection = collection;
        this.type = type;
    }

    public String collection() { return collection; }
    public Class<T> type() { return type; }

    @Override
    public List<T> findAll() {
        return store.readAll(collection, type);
    }

    @Override
    public List<T> findWhere(Predicate<T> predicate) {
        return findAll().stream().filter(predicate).toList();
    }

    @Override
    public Optional<T> findById(String id) {
        if (id == null) return Optional.empty();
        return findAll().stream().filter(e -> id.equals(idOf(e))).findFirst();
    }

    @Override
    public T save(T entity) {
        return store.mutate(collection, type, list -> {
            BeanWrapper bw = new BeanWrapperImpl(entity);
            Instant now = Instant.now();
            if (bw.getPropertyValue("id") == null) {
                bw.setPropertyValue("id", UUID.randomUUID().toString());
                if (bw.isWritableProperty("createdAt")) bw.setPropertyValue("createdAt", now);
            }
            if (bw.isWritableProperty("updatedAt")) bw.setPropertyValue("updatedAt", now);
            String id = (String) bw.getPropertyValue("id");
            list.removeIf(e -> id.equals(idOf(e)));
            list.add(entity);
            return entity;
        });
    }

    @Override
    public void deleteById(String id) {
        store.mutate(collection, type, list -> {
            list.removeIf(e -> id.equals(idOf(e)));
            return null;
        });
    }

    @Override
    public void deleteWhere(Predicate<T> predicate) {
        store.mutate(collection, type, list -> {
            list.removeIf(predicate);
            return null;
        });
    }

    private String idOf(T entity) {
        return (String) new BeanWrapperImpl(entity).getPropertyValue("id");
    }
}
