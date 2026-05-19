package io.synapse.platform.auth.mfa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mfa_credentials")
class MfaCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "type", nullable = false, length = 20)
    private String type = "totp";

    @Column(name = "secret_enc", nullable = false)
    private String secretEnc;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = false;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected MfaCredential() {
    }

    static MfaCredential create(UUID userId, String secretEnc) {
        MfaCredential credential = new MfaCredential();
        credential.userId = userId;
        credential.secretEnc = secretEnc;
        return credential;
    }

    void activate() {
        isActive = true;
        verifiedAt = Instant.now();
        updatedAt = Instant.now();
    }

    void replaceSecret(String newSecretEnc) {
        secretEnc = newSecretEnc;
        isActive = false;
        verifiedAt = null;
        updatedAt = Instant.now();
    }

    UUID getUserId() {
        return userId;
    }

    String getSecretEnc() {
        return secretEnc;
    }

    boolean isActive() {
        return isActive;
    }

    Instant getVerifiedAt() {
        return verifiedAt;
    }
}
