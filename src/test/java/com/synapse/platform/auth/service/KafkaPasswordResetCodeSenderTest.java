package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.synapse.platform.NotificationSend;
import com.synapse.platform.user.api.UserInfo;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class KafkaPasswordResetCodeSenderTest {

    private final KafkaTemplate<String, Object> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
    private final KafkaPasswordResetCodeSender sender = new KafkaPasswordResetCodeSender(
            kafkaTemplate,
            "platform.notification.notification-send-v1");

    @Test
    void send_shouldPublishEmailNotificationSendEvent() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UserInfo user = new UserInfo(userId, "user@example.com", "User", tenantId);
        given(kafkaTemplate.send(
                eq("platform.notification.notification-send-v1"),
                eq(userId.toString()),
                any(NotificationSend.class)))
                .willReturn(CompletableFuture.completedFuture(Mockito.mock(SendResult.class)));

        sender.send(user, "123456", OffsetDateTime.parse("2026-06-10T12:15:00+09:00"));

        ArgumentCaptor<NotificationSend> captor = ArgumentCaptor.forClass(NotificationSend.class);
        verify(kafkaTemplate).send(
                eq("platform.notification.notification-send-v1"),
                eq(userId.toString()),
                captor.capture());
        NotificationSend event = captor.getValue();
        assertThat(event.getTenantId().toString()).isEqualTo(tenantId.toString());
        assertThat(event.getUserId().toString()).isEqualTo(userId.toString());
        assertThat(event.getNotificationType().toString()).isEqualTo("PASSWORD_RESET_CODE");
        assertThat(event.getChannels()).containsExactly("EMAIL");
        assertThat(event.getEmailHtmlBody().toString()).contains("123456");
    }
}
