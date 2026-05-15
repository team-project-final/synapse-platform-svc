package com.synapse.platform.auth.jwt;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    boolean existsByTokenHash(String tokenHash);

    boolean existsByUserIdAndTokenHashAndExpiresAtAfter(UUID userId, String tokenHash, Instant now);

    long countByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
    void deleteAllByUserId(UUID userId);
}
