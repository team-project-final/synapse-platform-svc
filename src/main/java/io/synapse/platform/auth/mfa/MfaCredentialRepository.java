package io.synapse.platform.auth.mfa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface MfaCredentialRepository extends JpaRepository<MfaCredential, UUID> {

    Optional<MfaCredential> findByUserId(UUID userId);
}
