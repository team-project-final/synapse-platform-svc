package io.synapse.platform.auth.repository;

import io.synapse.platform.auth.domain.TenantMember;
import io.synapse.platform.auth.domain.TenantMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMemberRepository extends JpaRepository<TenantMember, TenantMemberId> {
}
