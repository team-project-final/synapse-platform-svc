package com.synapse.platform.audit.service;

import com.synapse.platform.audit.dto.AuditLogResponse;
import com.synapse.platform.audit.entity.AuditLog;
import com.synapse.platform.audit.repository.AuditLogRepository;
import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.avro.generic.GenericRecord;
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
    private static final int RETENTION_DAYS = 90;

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void processEvent(GenericRecord envelope) {
        try {
            AuditLog auditLog = toAuditLog(envelope);
            repository.save(auditLog);
        } catch (DataIntegrityViolationException exception) {
            log.info("Duplicate event skipped");
        }
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

    private AuditLog toAuditLog(GenericRecord envelope) {
        UUID eventId = UUID.fromString(requiredText(envelope, "id"));
        String type = requiredText(envelope, "type");
        GenericRecord data = PlatformAvroEvents.decodeUserRegistered(envelope);
        UUID userId = UUID.fromString(requiredText(data, "userId"));
        return AuditLog.of(
                eventId,
                actionFor(type),
                userId,
                "USER",
                userId.toString(),
                data.toString());
    }

    private String actionFor(String type) {
        if (PlatformAvroEvents.USER_REGISTERED_TYPE.equals(type)) {
            return "USER_REGISTERED";
        }
        throw new IllegalArgumentException("Unsupported audit event type: " + type);
    }

    private String requiredText(GenericRecord record, String fieldName) {
        Object rawValue = record.get(fieldName);
        String value = rawValue == null ? null : rawValue.toString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing audit event field: " + fieldName);
        }
        return value;
    }
}
