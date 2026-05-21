package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    boolean existsByTokenHash(String tokenHash);

    boolean existsByUserIdAndTokenHashAndExpiresAtAfter(UUID userId, String tokenHash, Instant now);

    long countByUserId(UUID userId);

    List<RefreshToken> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<RefreshToken> findByUserIdAndTokenHash(UUID userId, String tokenHash);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
    void deleteAllByUserId(UUID userId);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId AND r.tokenHash = :tokenHash")
    void deleteByUserIdAndTokenHash(UUID userId, String tokenHash);
}
