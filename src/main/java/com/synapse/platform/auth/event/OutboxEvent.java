package com.synapse.platform.auth.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    private static final int MAX_ATTEMPTS = 10;
    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 200)
    private String eventType;

    @Column(nullable = false, length = 4096)
    private byte[] payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status = OutboxEventStatus.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected OutboxEvent() {
    }

    public static OutboxEvent pending(String topic, String eventKey, String eventType, byte[] payload) {
        OutboxEvent event = new OutboxEvent();
        event.topic = topic;
        event.eventKey = eventKey;
        event.eventType = eventType;
        event.payload = payload.clone();
        event.status = OutboxEventStatus.PENDING;
        event.attempts = 0;
        event.nextAttemptAt = OffsetDateTime.now();
        return event;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (nextAttemptAt == null) {
            nextAttemptAt = createdAt;
        }
    }

    public void markPublished() {
        status = OutboxEventStatus.PUBLISHED;
        publishedAt = OffsetDateTime.now();
        lastError = null;
    }

    public void markPublishing(OffsetDateTime leaseExpiresAt) {
        status = OutboxEventStatus.PUBLISHING;
        nextAttemptAt = leaseExpiresAt;
    }

    public void markPublishFailed(Throwable exception) {
        attempts++;
        lastError = truncate(errorMessage(exception));
        if (attempts >= MAX_ATTEMPTS) {
            status = OutboxEventStatus.FAILED;
            return;
        }
        status = OutboxEventStatus.PENDING;
        nextAttemptAt = OffsetDateTime.now().plusSeconds(backoffSeconds());
    }

    private long backoffSeconds() {
        return Math.min(60L, 1L << Math.min(attempts, 6));
    }

    private String errorMessage(Throwable exception) {
        if (exception.getMessage() == null || exception.getMessage().isBlank()) {
            return exception.getClass().getName();
        }
        return exception.getMessage();
    }

    private String truncate(String value) {
        if (value.length() <= MAX_ERROR_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_ERROR_LENGTH);
    }

    public UUID getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getEventType() {
        return eventType;
    }

    public byte[] getPayload() {
        return payload.clone();
    }

    public OutboxEventStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public OffsetDateTime getNextAttemptAt() {
        return nextAttemptAt;
    }

    public OffsetDateTime getPublishedAt() {
        return publishedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
