package com.synapse.platform.auth.event;

import com.synapse.platform.UserRegistered;
import java.time.Instant;
import java.util.UUID;

public record UserRegisteredOutboxPayload(
        String eventId,
        String tenantId,
        long occurredAt,
        String userId,
        String email,
        String displayName) {

    static UserRegisteredOutboxPayload create(UUID userId, String email, String displayName, UUID tenantId) {
        return new UserRegisteredOutboxPayload(
                UUID.randomUUID().toString(),
                tenantId.toString(),
                Instant.now().toEpochMilli(),
                userId.toString(),
                email,
                displayName);
    }

    UserRegistered toRecord() {
        return UserRegistered.newBuilder()
                .setEventId(eventId)
                .setTenantId(tenantId)
                .setOccurredAt(occurredAt)
                .setTraceparent(null)
                .setUserId(userId)
                .setEmail(email)
                .setDisplayName(displayName)
                .build();
    }
}
