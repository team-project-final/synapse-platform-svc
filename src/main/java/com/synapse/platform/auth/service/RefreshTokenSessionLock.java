package com.synapse.platform.auth.service;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

@Component
class RefreshTokenSessionLock {

    private static final String LOCK_NAMESPACE = "refresh_tokens";

    private final JdbcTemplate jdbcTemplate;

    RefreshTokenSessionLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void acquire(UUID userId) {
        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtext(?), hashtext(?))",
                (ResultSetExtractor<Void>) resultSet -> null,
                LOCK_NAMESPACE,
                userId.toString());
    }
}
