package com.synapse.platform.user.api;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface UserApi {
    Optional<UserInfo> findById(UUID userId);

    Optional<UserInfo> findByEmail(String email);

    Optional<UserLoginCredential> findLoginCredentialByEmail(String email);

    boolean isLoginAllowed(UUID userId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    UserInfo createForOAuth(OAuthUserCreateCommand command);

    UserInfo createForEmailPassword(EmailPasswordUserCreateCommand command);

    LoginFailureResult recordFailedLogin(UUID userId, OffsetDateTime now);

    void recordSuccessfulLogin(UUID userId, OffsetDateTime now);
}
