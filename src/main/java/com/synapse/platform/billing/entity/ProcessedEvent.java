package com.synapse.platform.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    protected ProcessedEvent() {
    }

    @PrePersist
    void prePersist() {
        receivedAt = OffsetDateTime.now();
    }
}
