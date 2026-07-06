package com.moneymap.repository;

import com.moneymap.model.InviteToken;

import java.util.List;
import java.util.Optional;

/** Registration invite tokens for INVITE_ONLY mode (Section 01 §1.1 / §6.6). */
public interface InviteTokenRepository {

    List<InviteToken> findAll();

    Optional<InviteToken> findByToken(String token);

    InviteToken save(InviteToken token);
}
