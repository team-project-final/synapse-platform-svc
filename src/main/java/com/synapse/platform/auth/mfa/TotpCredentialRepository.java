package com.synapse.platform.auth.mfa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TotpCredentialRepository extends JpaRepository<TotpCredential, UUID> {

    Optional<TotpCredential> findByUserId(UUID userId);
}
