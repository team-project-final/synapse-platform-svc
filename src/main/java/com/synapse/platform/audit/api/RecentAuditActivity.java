package com.synapse.platform.audit.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RecentAuditActivity(
        UUID id,
        String action,
        UUID userId,
        String resourceType,
        String resourceId,
        OffsetDateTime createdAt) {
}
