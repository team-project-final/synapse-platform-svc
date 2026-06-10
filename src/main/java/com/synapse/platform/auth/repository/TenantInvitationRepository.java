package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.TenantInvitation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantInvitationRepository extends JpaRepository<TenantInvitation, UUID> {
    Optional<TenantInvitation> findByTenantIdAndEmailAndStatus(UUID tenantId, String email, String status);
}
