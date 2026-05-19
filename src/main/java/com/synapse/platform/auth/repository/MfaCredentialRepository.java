package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.MfaCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MfaCredentialRepository extends JpaRepository<MfaCredential, UUID> {

    Optional<MfaCredential> findByUserId(UUID userId);
}
