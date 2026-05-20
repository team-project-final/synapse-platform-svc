package com.synapse.platform.notification.repository;

import com.synapse.platform.notification.entity.DeviceToken;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, UUID> {

    Optional<DeviceToken> findByToken(String token);

    long countByUserId(UUID userId);

    List<DeviceToken> findByUserId(UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            INSERT INTO device_tokens (id, tenant_id, user_id, token, platform, created_at, updated_at)
            VALUES (:id, :tenantId, :userId, :token, :platform, NOW(), NOW())
            ON CONFLICT (token) DO UPDATE
              SET tenant_id  = EXCLUDED.tenant_id,
                  user_id    = EXCLUDED.user_id,
                  updated_at = NOW()
            """, nativeQuery = true)
    void upsert(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("userId") UUID userId,
            @Param("token") String token,
            @Param("platform") String platform);
}
