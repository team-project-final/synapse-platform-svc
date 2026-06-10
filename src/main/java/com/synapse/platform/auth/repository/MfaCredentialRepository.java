package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.MfaCredential;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaCredentialRepository extends JpaRepository<MfaCredential, UUID> {

    Optional<MfaCredential> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from MfaCredential credential where credential.userId = :userId")
    Optional<MfaCredential> findByUserIdForUpdate(@Param("userId") UUID userId);
}
