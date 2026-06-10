package com.synapse.platform.auth.service;

import com.synapse.platform.NotificationSend;
import com.synapse.platform.user.api.UserInfo;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(name = "eventKafkaTemplate")
@ConditionalOnProperty(prefix = "synapse.kafka", name = "enabled", havingValue = "true")
public class KafkaPasswordResetCodeSender implements PasswordResetCodeSender {

    private static final Logger log = LoggerFactory.getLogger(KafkaPasswordResetCodeSender.class);
    private static final String NOTIFICATION_TYPE = "PASSWORD_RESET_CODE";
    private static final String TITLE = "Password reset code";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String notificationTopic;

    public KafkaPasswordResetCodeSender(
            @Qualifier("eventKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.notification-send:platform.notification.notification-send-v1}")
                    String notificationTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.notificationTopic = notificationTopic;
    }

    @Override
    public void send(UserInfo user, String code, OffsetDateTime expiresAt) {
        UUID tenantId = user.defaultTenantId() == null ? user.id() : user.defaultTenantId();
        NotificationSend event = NotificationSend.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setTenantId(tenantId.toString())
                .setOccurredAt(Instant.now().toEpochMilli())
                .setTraceparent(null)
                .setUserId(user.id().toString())
                .setNotificationType(NOTIFICATION_TYPE)
                .setChannels(List.of("EMAIL"))
                .setTitle(TITLE)
                .setBody("A password reset code was requested for your Synapse account.")
                .setEmailSubject("Your Synapse password reset code")
                .setEmailHtmlBody(emailBody(code, expiresAt))
                .setData(Map.of("purpose", "password-reset"))
                .build();
        kafkaTemplate.send(notificationTopic, user.id().toString(), event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.warn("Password reset code notification publish failed: userId={}", user.id(), exception);
                    }
                });
    }

    private String emailBody(String code, OffsetDateTime expiresAt) {
        return """
                <p>Your Synapse password reset code is:</p>
                <p><strong>%s</strong></p>
                <p>This code expires at %s.</p>
                """.formatted(code, expiresAt);
    }
}
