package com.synapse.platform.user.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UserLoginCredential(
        UUID id,
        String email,
        String passwordHash,
        int failedLoginCount,
        OffsetDateTime lockedUntil
) {
}
