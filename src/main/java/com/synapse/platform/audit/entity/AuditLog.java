package com.synapse.platform.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "resource_type", length = 50)
    private String resourceType;

    @Column(name = "resource_id", length = 200)
    private String resourceId;

    @Column(name = "old_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String newValue;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AuditLog() {
    }

    public static AuditLog of(
            UUID eventId,
            String action,
            UUID userId,
            String resourceType,
            String resourceId,
            String newValue) {
        AuditLog log = new AuditLog();
        log.eventId = eventId;
        log.action = action;
        log.userId = userId;
        log.resourceType = resourceType;
        log.resourceId = resourceId;
        log.newValue = newValue;
        return log;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getAction() {
        return action;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
