package com.synapse.platform.auth.service;

import java.time.OffsetDateTime;

public record PasswordResetResult(
        String resetToken,
        OffsetDateTime expiresAt
) {
}
