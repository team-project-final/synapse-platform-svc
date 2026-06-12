package com.synapse.platform.audit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.engagement.BadgeEarned;
import com.synapse.engagement.LevelUp;
import com.synapse.knowledge.NoteCreated;
import com.synapse.knowledge.NoteUpdated;
import com.synapse.learning.ReviewCompleted;
import com.synapse.platform.NotificationSend;
import com.synapse.platform.UserRegistered;
import com.synapse.platform.audit.dto.AuditLogResponse;
import com.synapse.platform.audit.entity.AuditLog;
import com.synapse.platform.audit.repository.AuditLogRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int RETENTION_DAYS = 90;

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void processEvent(UserRegistered event) {
        UUID eventId = UUID.fromString(requiredText(event.getEventId(), "eventId"));
        UUID userId = UUID.fromString(requiredText(event.getUserId(), "userId"));
        save(AuditLog.of(eventId, "USER_REGISTERED", userId, "USER", userId.toString(), event.toString()));
    }

    public void processEvent(NoteCreated event) {
        save(AuditLog.of(
                UUID.fromString(event.getEventId().toString()),
                "NOTE_CREATED",
                parseUuidOrNull(event.getUserId()),
                "NOTE",
                event.getNoteId().toString(),
                event.toString()));
    }

    public void processEvent(NoteUpdated event) {
        save(AuditLog.of(
                UUID.fromString(event.getEventId().toString()),
                "NOTE_UPDATED",
                parseUuidOrNull(event.getUserId()),
                "NOTE",
                event.getNoteId().toString(),
                event.toString()));
    }

    public void processEvent(ReviewCompleted event) {
        save(AuditLog.of(
                UUID.fromString(event.getEventId().toString()),
                "REVIEW_COMPLETED",
                parseUuidOrNull(event.getUserId()),
                "CARD",
                event.getCardId().toString(),
                event.toString()));
    }

    public void processEvent(BadgeEarned event) {
        save(AuditLog.of(
                UUID.fromString(event.getEventId().toString()),
                "BADGE_EARNED",
                parseUuidOrNull(event.getUserId()),
                "BADGE",
                event.getBadgeId().toString(),
                event.toString()));
    }

    public void processEvent(LevelUp event) {
        save(AuditLog.of(
                UUID.fromString(event.getEventId().toString()),
                "LEVEL_UP",
                parseUuidOrNull(event.getUserId()),
                "USER",
                event.getUserId().toString(),
                event.toString()));
    }

    public void processEvent(NotificationSend event) {
        save(AuditLog.of(
                UUID.fromString(requiredText(event.getEventId(), "eventId")),
                "NOTIFICATION_SEND",
                parseUuidOrNull(event.getUserId()),
                "USER",
                requiredText(event.getUserId(), "userId"),
                notificationAuditPayload(event)));
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void deleteOldLogs() {
        repository.deleteByCreatedAtBefore(OffsetDateTime.now().minusDays(RETENTION_DAYS));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditLogs(String action, UUID userId, Pageable pageable) {
        if (action != null && !action.isBlank()) {
            return repository.findByAction(action, pageable).map(AuditLogResponse::from);
        }
        if (userId != null) {
            return repository.findByUserId(userId, pageable).map(AuditLogResponse::from);
        }
        return repository.findAll(pageable).map(AuditLogResponse::from);
    }

    private void save(AuditLog auditLog) {
        try {
            repository.save(auditLog);
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate event skipped");
        }
    }

    /**
     * Parses a UUID from the given raw value. Returns null if the value is null,
     * blank, or not a valid UUID (e.g. engagement userId is a Long-stringified value).
     */
    private UUID parseUuidOrNull(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(raw.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String requiredText(Object rawValue, String fieldName) {
        String value = rawValue == null ? null : rawValue.toString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing audit event field: " + fieldName);
        }
        return value;
    }

    private String notificationAuditPayload(NotificationSend event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", requiredText(event.getEventId(), "eventId"));
        payload.put("tenantId", requiredText(event.getTenantId(), "tenantId"));
        payload.put("occurredAt", event.getOccurredAt());
        payload.put("traceparent", nullableText(event.getTraceparent()));
        payload.put("userId", requiredText(event.getUserId(), "userId"));
        payload.put("notificationType", requiredText(event.getNotificationType(), "notificationType"));
        payload.put("channels", textList(event.getChannels()));
        payload.put("contentRedacted", true);
        return writeJson(payload);
    }

    private List<String> textList(List<?> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(Object::toString)
                .toList();
    }

    private String nullableText(Object rawValue) {
        return rawValue == null ? null : rawValue.toString();
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize audit payload", exception);
        }
    }
}
