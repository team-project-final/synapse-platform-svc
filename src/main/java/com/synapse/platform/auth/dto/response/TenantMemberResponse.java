package com.synapse.platform.auth.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TenantMemberResponse(
        UUID userId,
        String email,
        String displayName,
        String role,
        OffsetDateTime joinedAt
) {
}
