package com.synapse.platform.auth.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled = false;

    @Column(name = "password_changed_at")
    private OffsetDateTime passwordChangedAt;

    @Column(name = "last_login_at")
    private OffsetDateTime lastLoginAt;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @Column(name = "locked_until")
    private OffsetDateTime lockedUntil;

    @Column(name = "default_tenant_id")
    private UUID defaultTenantId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "anonymized_at")
    private OffsetDateTime anonymizedAt;

    protected User() {
    }

    public static User ofOAuth(String email, String username, String displayName, String avatarUrl) {
        User user = new User();
        user.email = email;
        user.username = username;
        user.displayName = displayName;
        user.avatarUrl = avatarUrl;
        return user;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    public void updateDefaultTenantId(UUID tenantId) {
        defaultTenantId = tenantId;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public UUID getDefaultTenantId() {
        return defaultTenantId;
    }
}
