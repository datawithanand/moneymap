package com.moneymap.repository;

import com.moneymap.model.RememberMeToken;

import java.time.Instant;
import java.util.List;

public interface RememberMeTokenRepository {

    List<RememberMeToken> findAll();

    List<RememberMeToken> findByUserId(String userId);

    RememberMeToken save(RememberMeToken token);

    /** A new login invalidates any previous token for the user (Section 01 §2.2). */
    void deleteByUserId(String userId);

    void deleteExpired(Instant now);
}
