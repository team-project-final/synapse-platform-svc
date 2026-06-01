package com.synapse.platform.auth.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminTenantInfo(
        UUID id,
        String name,
        String slug,
        String plan,
        String status,
        OffsetDateTime createdAt) {
}
