package com.synapse.platform.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_members")
@IdClass(TenantMemberId.class)
public class TenantMember {

    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String role = "member";

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    protected TenantMember() {
    }

    public static TenantMember ofOwner(UUID tenantId, UUID userId) {
        TenantMember member = new TenantMember();
        member.tenantId = tenantId;
        member.userId = userId;
        member.role = "owner";
        member.joinedAt = OffsetDateTime.now();
        return member;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }
}
