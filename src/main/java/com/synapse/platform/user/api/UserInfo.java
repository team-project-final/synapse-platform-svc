package com.synapse.platform.user.api;

import java.util.UUID;

public record UserInfo(
        UUID id,
        String email,
        String displayName,
        UUID defaultTenantId
) {
}
