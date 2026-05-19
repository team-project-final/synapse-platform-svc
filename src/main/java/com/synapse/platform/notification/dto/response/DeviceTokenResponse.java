package com.synapse.platform.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DeviceTokenResponse(
        UUID id,
        String platform,
        boolean isActive,
        Instant createdAt
) {
}
