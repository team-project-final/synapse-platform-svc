package com.synapse.platform.user.api;

import java.util.UUID;

public record EmailPasswordUserCreateCommand(
        String email,
        String username,
        String passwordHash,
        UUID defaultTenantId
) {
}
