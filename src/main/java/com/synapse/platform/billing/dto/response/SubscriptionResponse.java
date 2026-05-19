package com.synapse.platform.billing.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String planCode,
        String status,
        OffsetDateTime currentPeriodEnd,
        String stripeSubscriptionId
) {
}
