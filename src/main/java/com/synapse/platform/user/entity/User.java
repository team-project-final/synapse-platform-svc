package com.synapse.platform.user.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "users")
@SQLRestriction("deleted_at IS NULL")
public class User {

    private static final int MAX_FAILED_LOGIN_COUNT = 5;
    private static final long LOCK_MINUTES = 15;

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

    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

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

    public static User ofEmailPassword(String email, String username, String passwordHash, UUID defaultTenantId) {
        User user = new User();
        user.email = email;
        user.username = username;
        user.passwordHash = passwordHash;
        user.displayName = username;
        user.defaultTenantId = defaultTenantId;
        user.passwordChangedAt = OffsetDateTime.now();
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

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public boolean isLocked(OffsetDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordFailedLogin(OffsetDateTime now) {
        failedLoginCount++;
        if (failedLoginCount >= MAX_FAILED_LOGIN_COUNT) {
            lockedUntil = now.plusMinutes(LOCK_MINUTES);
        }
        updatedAt = now;
    }

    public void recordSuccessfulLogin(OffsetDateTime now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    public void suspend() {
        status = UserStatus.SUSPENDED;
        suspendedAt = OffsetDateTime.now();
        updatedAt = suspendedAt;
    }

    public void activate() {
        status = UserStatus.ACTIVE;
        suspendedAt = null;
        updatedAt = OffsetDateTime.now();
    }

    public void softDelete() {
        status = UserStatus.DELETED;
        OffsetDateTime now = OffsetDateTime.now();
        suspendedAt = null;
        deletedAt = now;
        anonymizedAt = now;
        email = "deleted_" + id + "@deleted.invalid";
        username = "deleted_" + id;
        displayName = "Deleted User";
        updatedAt = now;
    }

    public void updateProfile(String displayName) {
        this.displayName = displayName;
        updatedAt = OffsetDateTime.now();
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        passwordChangedAt = OffsetDateTime.now();
        updatedAt = passwordChangedAt;
    }

    public void resetPassword(String passwordHash) {
        changePassword(passwordHash);
        failedLoginCount = 0;
        lockedUntil = null;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    public OffsetDateTime getLockedUntil() {
        return lockedUntil;
    }

    public UUID getDefaultTenantId() {
        return defaultTenantId;
    }

    public UserStatus getStatus() {
        return status;
    }

    public OffsetDateTime getSuspendedAt() {
        return suspendedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }
}
