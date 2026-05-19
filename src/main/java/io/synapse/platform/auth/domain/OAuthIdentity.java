package io.synapse.platform.auth.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "oauth_identities")
public class OAuthIdentity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String provider;

    @Column(name = "provider_user_id", nullable = false)
    private String providerUserId;

    @Column
    private String email;

    @Column(name = "access_token_enc")
    private String accessTokenEnc;

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata = "{}";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected OAuthIdentity() {
    }

    public static OAuthIdentity of(UUID userId, String provider, String providerUserId, String email) {
        return of(userId, provider, providerUserId, email, null);
    }

    public static OAuthIdentity of(
            UUID userId,
            String provider,
            String providerUserId,
            String email,
            String accessTokenEnc) {
        OAuthIdentity identity = new OAuthIdentity();
        identity.userId = userId;
        identity.provider = provider;
        identity.providerUserId = providerUserId;
        identity.email = email;
        identity.accessTokenEnc = accessTokenEnc;
        return identity;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderUserId() {
        return providerUserId;
    }

    public String getEmail() {
        return email;
    }

    public String getAccessTokenEnc() {
        return accessTokenEnc;
    }

    public void updateAccessTokenEnc(String accessTokenEnc) {
        if (accessTokenEnc != null) {
            this.accessTokenEnc = accessTokenEnc;
        }
    }
}
