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
@Table(name = "password_reset_requests")
public class PasswordResetRequest {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_VERIFIED = "verified";
    public static final String STATUS_USED = "used";
    public static final String STATUS_EXPIRED = "expired";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "reset_token_hash", length = 64)
    private String resetTokenHash;

    @Column(nullable = false, length = 20)
    private String status = STATUS_PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected PasswordResetRequest() {
    }

    public static PasswordResetRequest create(
            UUID userId,
            String email,
            String codeHash,
            OffsetDateTime expiresAt) {
        PasswordResetRequest request = new PasswordResetRequest();
        request.userId = userId;
        request.email = email;
        request.codeHash = codeHash;
        request.expiresAt = expiresAt;
        return request;
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

    public static String hash(String rawValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawValue.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(64);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    public boolean isVerified() {
        return STATUS_VERIFIED.equals(status);
    }

    public boolean isExpired(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public void recordFailedAttempt(int maxAttempts) {
        attempts++;
        if (attempts >= maxAttempts) {
            markExpired();
        }
    }

    public void markVerified(String rawResetToken, OffsetDateTime resetTokenExpiresAt) {
        status = STATUS_VERIFIED;
        resetTokenHash = hash(rawResetToken);
        expiresAt = resetTokenExpiresAt;
        verifiedAt = OffsetDateTime.now();
    }

    public void markUsed() {
        status = STATUS_USED;
        usedAt = OffsetDateTime.now();
    }

    public void markExpired() {
        status = STATUS_EXPIRED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public String getResetTokenHash() {
        return resetTokenHash;
    }

    public String getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }
}
