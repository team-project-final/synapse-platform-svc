package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.OAuthIdentity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, UUID> {

    Optional<OAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<OAuthIdentity> findByUserIdAndProviderIgnoreCase(UUID userId, String provider);

    List<OAuthIdentity> findAllByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OAuthIdentity o where o.userId = :userId")
    List<OAuthIdentity> findAllByUserIdForUpdate(@Param("userId") UUID userId);

    long countByUserId(UUID userId);
}
