package com.synapse.platform.auth.event;

public enum OutboxEventStatus {
    PENDING,
    PUBLISHING,
    PUBLISHED,
    FAILED
}
