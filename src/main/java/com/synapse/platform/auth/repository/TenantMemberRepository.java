package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.TenantMember;
import com.synapse.platform.auth.entity.TenantMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMemberRepository extends JpaRepository<TenantMember, TenantMemberId> {
}
