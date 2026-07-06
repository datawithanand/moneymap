package com.moneymap.repository;

import com.moneymap.model.HouseholdMember;

import java.util.List;
import java.util.Optional;

/** Household Members list — the familyMemberTag source for every asset module (Section 17 §1.7). */
public interface HouseholdMemberRepository {

    List<HouseholdMember> findByOwnerId(String ownerId);

    Optional<HouseholdMember> findById(String id);

    HouseholdMember save(HouseholdMember member);

    void deleteById(String id);

    void deleteByOwnerId(String ownerId);
}
