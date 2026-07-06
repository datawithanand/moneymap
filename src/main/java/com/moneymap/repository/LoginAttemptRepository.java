package com.moneymap.repository;

import com.moneymap.model.LoginAttempt;

import java.time.Instant;
import java.util.List;

/** Failed-login telemetry, rolling 30-day retention (Section 02). */
public interface LoginAttemptRepository {

    LoginAttempt save(LoginAttempt attempt);

    List<LoginAttempt> findSince(Instant since);

    void deleteOlderThan(Instant cutoff);
}
