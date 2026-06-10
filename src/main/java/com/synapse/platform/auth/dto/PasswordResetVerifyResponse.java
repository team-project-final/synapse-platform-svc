package com.synapse.platform.auth.dto;

import java.time.OffsetDateTime;

public record PasswordResetVerifyResponse(
        String resetToken,
        OffsetDateTime expiresAt
) {
}
