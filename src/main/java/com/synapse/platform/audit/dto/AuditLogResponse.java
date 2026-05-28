package com.synapse.platform.audit.dto;

import com.synapse.platform.audit.entity.AuditLog;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID eventId,
        String action,
        UUID userId,
        String resourceType,
        String resourceId,
        String oldValue,
        String newValue,
        String ipAddress,
        String userAgent,
        OffsetDateTime createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getEventId(),
                log.getAction(),
                log.getUserId(),
                log.getResourceType(),
                log.getResourceId(),
                log.getOldValue(),
                log.getNewValue(),
                log.getIpAddress(),
                log.getUserAgent(),
                log.getCreatedAt());
    }
}
