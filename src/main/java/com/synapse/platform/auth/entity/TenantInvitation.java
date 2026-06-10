package com.synapse.platform.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "tenant_invitations")
public class TenantInvitation {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_EXPIRED = "expired";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, length = 20)
    private String role;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TenantInvitation() {
    }

    public static TenantInvitation create(
            UUID tenantId,
            String email,
            String role,
            String rawToken,
            UUID invitedBy,
            OffsetDateTime expiresAt) {
        TenantInvitation invitation = new TenantInvitation();
        invitation.tenantId = tenantId;
        invitation.email = email;
        invitation.role = role;
        invitation.tokenHash = hash(rawToken);
        invitation.status = STATUS_PENDING;
        invitation.invitedBy = invitedBy;
        invitation.expiresAt = expiresAt;
        return invitation;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(64);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public boolean isActivePending(OffsetDateTime now) {
        return STATUS_PENDING.equals(status) && expiresAt.isAfter(now);
    }

    public void markExpired() {
        status = STATUS_EXPIRED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getStatus() {
        return status;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
