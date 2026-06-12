package com.synapse.platform.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.NotificationSend;
import com.synapse.platform.UserRegistered;
import com.synapse.platform.audit.entity.AuditLog;
import com.synapse.platform.audit.repository.AuditLogRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuditLogRepository repository;

    @Test
    void processEvent_userRegisteredSpecificRecord_shouldStoreAuditLog() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserRegistered event = userRegistered(
                UUID.randomUUID(),
                userId,
                tenantId,
                "new@example.com",
                "New User");
        AuditLogService service = service();

        service.processEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getEventId()).isEqualTo(UUID.fromString(event.getEventId().toString()));
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
        UserRegistered event = userRegistered(
                UUID.randomUUID(),
                userId,
                tenantId,
                "new@example.com",
                "New User");
        given(repository.save(any(AuditLog.class)))
                .willThrow(new DataIntegrityViolationException("uq_audit_logs_event_id"));
        AuditLogService service = service();

        assertThatCode(() -> service.processEvent(event)).doesNotThrowAnyException();

        verify(repository).save(any(AuditLog.class));
    }

    @Test
    void processEvent_notificationSend_shouldStoreRedactedAuditPayload() throws Exception {
        UUID userId = UUID.randomUUID();
        NotificationSend event = NotificationSend.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setTenantId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toEpochMilli())
                .setTraceparent(null)
                .setUserId(userId.toString())
                .setNotificationType("PASSWORD_RESET_CODE")
                .setChannels(List.of("EMAIL"))
                .setTitle("Password reset code")
                .setBody("A password reset code was requested.")
                .setEmailSubject("Your Synapse password reset code")
                .setEmailHtmlBody("<p><strong>123456</strong></p>")
                .setData(Map.of("resetCode", "123456"))
                .build();
        AuditLogService service = service();

        service.processEvent(event);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog saved = captor.getValue();
        JsonNode payload = objectMapper.readTree(saved.getNewValue());
        assertThat(saved.getAction()).isEqualTo("NOTIFICATION_SEND");
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(payload.get("notificationType").asText()).isEqualTo("PASSWORD_RESET_CODE");
        assertThat(payload.get("contentRedacted").asBoolean()).isTrue();
        assertThat(payload.has("body")).isFalse();
        assertThat(payload.has("emailHtmlBody")).isFalse();
        assertThat(payload.has("data")).isFalse();
        assertThat(saved.getNewValue())
                .doesNotContain("123456")
                .doesNotContain("resetCode")
                .doesNotContain("emailHtmlBody");
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

    private static UserRegistered userRegistered(
            UUID eventId,
            UUID userId,
            UUID tenantId,
            String email,
            String displayName) {
        return UserRegistered.newBuilder()
                .setEventId(eventId.toString())
                .setTenantId(tenantId.toString())
                .setOccurredAt(1717000000000L)
                .setTraceparent(null)
                .setUserId(userId.toString())
                .setEmail(email)
                .setDisplayName(displayName)
                .build();
    }
}
