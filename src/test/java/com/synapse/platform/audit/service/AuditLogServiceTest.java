package com.synapse.platform.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.synapse.platform.audit.entity.AuditLog;
import com.synapse.platform.audit.repository.AuditLogRepository;
import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository repository;

    @Test
    void processEvent_userRegisteredCloudEvent_shouldStoreAuditLog() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        GenericRecord event = PlatformAvroEvents.userRegisteredEnvelope(
                userId,
                "new@example.com",
                "New User",
                tenantId);
        AuditLogService service = service();

        service.processEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(UUID.fromString(event.get("id").toString()));
        assertThat(saved.getAction()).isEqualTo("USER_REGISTERED");
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getResourceType()).isEqualTo("USER");
        assertThat(saved.getResourceId()).isEqualTo(userId.toString());
        assertThat(saved.getNewValue()).contains("new@example.com");
    }

    @Test
    void processEvent_duplicateEvent_shouldSkipWithoutThrowing() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        GenericRecord event = PlatformAvroEvents.userRegisteredEnvelope(
                userId,
                "new@example.com",
                "New User",
                tenantId);
        given(repository.save(any(AuditLog.class)))
                .willThrow(new DataIntegrityViolationException("uq_audit_logs_event_id"));
        AuditLogService service = service();

        assertThatCode(() -> service.processEvent(event)).doesNotThrowAnyException();

        verify(repository).save(any(AuditLog.class));
    }

    @Test
    void getAuditLogs_actionFilter_shouldQueryByAction() {
        var pageable = PageRequest.of(0, 20);
        given(repository.findByAction("USER_REGISTERED", pageable)).willReturn(new PageImpl<>(List.of()));
        AuditLogService service = service();

        assertThat(service.getAuditLogs("USER_REGISTERED", null, pageable)).isEmpty();

        verify(repository).findByAction("USER_REGISTERED", pageable);
    }

    @Test
    void getAuditLogs_userFilterWithoutAction_shouldQueryByUserId() {
        UUID userId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 20);
        given(repository.findByUserId(userId, pageable)).willReturn(new PageImpl<>(List.of()));
        AuditLogService service = service();

        assertThat(service.getAuditLogs("", userId, pageable)).isEmpty();

        verify(repository).findByUserId(userId, pageable);
    }

    @Test
    void deleteOldLogs_shouldDeleteLogsOlderThanNinetyDays() {
        AuditLogService service = service();

        service.deleteOldLogs();

        ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).deleteByCreatedAtBefore(captor.capture());
        assertThat(captor.getValue()).isBefore(OffsetDateTime.now().minusDays(89));
    }

    private AuditLogService service() {
        return new AuditLogService(repository);
    }
}
