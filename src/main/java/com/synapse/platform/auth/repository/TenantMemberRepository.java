package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.TenantMember;
import com.synapse.platform.auth.entity.TenantMemberId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMemberRepository extends JpaRepository<TenantMember, TenantMemberId> {
    Optional<TenantMember> findByTenantIdAndUserId(UUID tenantId, UUID userId);

    Page<TenantMember> findByTenantId(UUID tenantId, Pageable pageable);

    java.util.List<TenantMember> findByTenantId(UUID tenantId, Sort sort);

    long countByTenantIdAndRole(UUID tenantId, String role);
}
