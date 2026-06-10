package com.synapse.platform.user.api;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserApi {
    Optional<UserInfo> findById(UUID userId);

    Optional<UserInfo> findByEmail(String email);

    List<UserSummary> findSummariesByIds(Collection<UUID> userIds);

    Optional<UserLoginCredential> findLoginCredentialByEmail(String email);

    boolean isLoginAllowed(UUID userId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    boolean hasPasswordLogin(UUID userId);

    List<String> findRoles(UUID userId);

    String getNotificationPreferences(UUID userId);

    String updateNotificationPreferences(UUID userId, String notificationPreferences);

    UserInfo createForOAuth(OAuthUserCreateCommand command);

    UserInfo createForEmailPassword(EmailPasswordUserCreateCommand command);

    LoginFailureResult recordFailedLogin(UUID userId, OffsetDateTime now);

    void recordSuccessfulLogin(UUID userId, OffsetDateTime now);

    void resetPassword(UUID userId, String newPassword);
}
