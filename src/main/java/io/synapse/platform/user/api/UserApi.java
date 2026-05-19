package io.synapse.platform.user.api;

import java.util.Optional;
import java.util.UUID;

public interface UserApi {
    Optional<UserInfo> findById(UUID userId);

    Optional<UserInfo> findByEmail(String email);

    UserInfo createForOAuth(OAuthUserCreateCommand command);
}
