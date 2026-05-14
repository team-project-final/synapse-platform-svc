package com.synapse.platform.auth.mfa;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "totp_credentials")
public class TotpCredential {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(nullable = false)
    private String secret;

    @Column(name = "secret_iv", nullable = false)
    private String secretIv;

    @Column(nullable = false)
    private boolean enabled = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TotpCredential() {
    }

    public static TotpCredential create(UUID userId, String secret, String secretIv) {
        TotpCredential credential = new TotpCredential();
        credential.userId = userId;
        credential.secret = secret;
        credential.secretIv = secretIv;
        return credential;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void enable() {
        enabled = true;
    }

    public void replaceSecret(String secret, String secretIv) {
        this.secret = secret;
        this.secretIv = secretIv;
        enabled = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getSecret() {
        return secret;
    }

    public String getSecretIv() {
        return secretIv;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
