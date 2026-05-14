package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.domain.OAuthIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuthIdentityRepository extends JpaRepository<OAuthIdentity, UUID> {

    Optional<OAuthIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);
}
