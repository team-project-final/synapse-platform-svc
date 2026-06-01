package com.synapse.platform.audit.service;

import com.synapse.platform.UserRegistered;
import com.synapse.platform.audit.dto.AuditLogResponse;
import com.synapse.platform.audit.entity.AuditLog;
import com.synapse.platform.audit.repository.AuditLogRepository;
import java.time.OffsetDateTime;
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
    private static final int RETENTION_DAYS = 90;

    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void processEvent(UserRegistered event) {
        try {
            AuditLog auditLog = toAuditLog(event);
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

    private AuditLog toAuditLog(UserRegistered event) {
        UUID eventId = UUID.fromString(requiredText(event.getEventId(), "eventId"));
        UUID userId = UUID.fromString(requiredText(event.getUserId(), "userId"));
        return AuditLog.of(
                eventId,
                "USER_REGISTERED",
                userId,
                "USER",
                userId.toString(),
                event.toString());
    }

    private String requiredText(Object rawValue, String fieldName) {
        String value = rawValue == null ? null : rawValue.toString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing audit event field: " + fieldName);
        }
        return value;
    }
}
