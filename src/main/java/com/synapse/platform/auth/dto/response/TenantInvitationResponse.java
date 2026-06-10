package com.synapse.platform.auth.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantInvitationResponse(
        UUID id,
        String email,
        String role,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
}
