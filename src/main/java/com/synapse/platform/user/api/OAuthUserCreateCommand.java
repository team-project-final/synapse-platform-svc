package com.synapse.platform.user.api;

import java.util.UUID;

public record OAuthUserCreateCommand(
        String email,
        String slug,
        String displayName,
        String avatarUrl,
        UUID defaultTenantId
) {
}
