package com.moneymap.repository.json;

import com.moneymap.model.HouseholdMember;
import com.moneymap.repository.HouseholdMemberRepository;
import com.moneymap.repository.JsonCollectionStore;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JsonHouseholdMemberRepository implements HouseholdMemberRepository {

    private static final String COLLECTION = "household_members";

    private final JsonCollectionStore store;

    public JsonHouseholdMemberRepository(JsonCollectionStore store) {
        this.store = store;
    }

    @Override
    public List<HouseholdMember> findByOwnerId(String ownerId) {
        return store.readAll(COLLECTION, HouseholdMember.class).stream()
                .filter(m -> m.getOwnerId().equals(ownerId))
                .toList();
    }

    @Override
    public Optional<HouseholdMember> findById(String id) {
        return store.readAll(COLLECTION, HouseholdMember.class).stream()
                .filter(m -> m.getId().equals(id))
                .findFirst();
    }

    @Override
    public HouseholdMember save(HouseholdMember member) {
        return store.mutate(COLLECTION, HouseholdMember.class, list -> {
            Instant now = Instant.now();
            if (member.getId() == null) {
                member.setId(UUID.randomUUID().toString());
                member.setCreatedAt(now);
            }
            member.setUpdatedAt(now);
            list.removeIf(m -> m.getId().equals(member.getId()));
            list.add(member);
            return member;
        });
    }

    @Override
    public void deleteById(String id) {
        store.mutate(COLLECTION, HouseholdMember.class, list -> {
            // The seeded "Self" entry cannot be deleted (Section 05 opening note / Section 17 §1.7)
            list.removeIf(m -> m.getId().equals(id) && !m.isDefaultEntry());
            return null;
        });
    }

    @Override
    public void deleteByOwnerId(String ownerId) {
        store.mutate(COLLECTION, HouseholdMember.class, list -> {
            list.removeIf(m -> m.getOwnerId().equals(ownerId));
            return null;
        });
    }
}
