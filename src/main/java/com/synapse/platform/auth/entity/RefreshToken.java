package com.synapse.platform.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "device_fingerprint")
    private String deviceFingerprint;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {
    }

    public static RefreshToken of(
            UUID userId,
            String rawToken,
            String deviceFingerprint,
            String ipAddress,
            Instant expiresAt) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.userId = userId;
        refreshToken.tokenHash = hash(rawToken);
        refreshToken.deviceFingerprint = deviceFingerprint;
        refreshToken.ipAddress = ipAddress;
        refreshToken.expiresAt = expiresAt;
        return refreshToken;
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

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getDeviceFingerprint() {
        return deviceFingerprint;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
