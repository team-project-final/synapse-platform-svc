package com.synapse.platform.notification.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.synapse.platform.NotificationSend;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.entity.NotificationStatus;
import com.synapse.platform.notification.repository.NotificationRepository;
import com.synapse.platform.notification.service.FcmPushService;
import com.synapse.platform.notification.service.SesEmailService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"platform.notification.notification-send-v1"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class NotificationKafkaConsumerIT {

    private static final String TOPIC = "platform.notification.notification-send-v1";

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    @Qualifier("eventKafkaTemplate")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @MockitoBean
    private FcmPushService fcmPushService;

    @MockitoBean
    private SesEmailService sesEmailService;

    @BeforeEach
    void cleanUp() {
        notificationRepository.deleteAll();
        reset(fcmPushService, sesEmailService);
    }

    @Test
    void notificationSendWithFcmChannel_shouldSendPushAndStoreSentNotification() {
        UUID userId = UUID.randomUUID();
        NotificationSend event = event(userId, List.of("FCM"));

        kafkaTemplate.send(TOPIC, event.getTenantId(), event);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(fcmPushService).sendToUser(eq(userId), eq("Review due"), eq("A card is ready."), any());
            verify(sesEmailService, never()).sendToUser(any(), any(), any());
            assertThat(notificationRepository.findAll())
                    .singleElement()
                    .satisfies(notification -> {
                        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.FCM);
                        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
                    });
        });
    }

    @Test
    void notificationSendWithEmailChannel_shouldSendEmailAndStoreSentNotification() {
        UUID userId = UUID.randomUUID();
        NotificationSend event = event(userId, List.of("EMAIL"));

        kafkaTemplate.send(TOPIC, event.getTenantId(), event);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(sesEmailService).sendToUser(userId, "Review due email", "<p>A card is ready.</p>");
            verify(fcmPushService, never()).sendToUser(any(), any(), any(), any());
            assertThat(notificationRepository.findAll())
                    .singleElement()
                    .satisfies(notification -> {
                        assertThat(notification.getChannel()).isEqualTo(NotificationChannel.EMAIL);
                        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
                    });
        });
    }

    @Test
    void aiCardsReadyNotificationSendWithBothChannels_shouldSendPushAndEmail() {
        UUID userId = UUID.randomUUID();
        NotificationSend event = event(userId, List.of("FCM", "EMAIL"));

        kafkaTemplate.send(TOPIC, event.getTenantId(), event);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(fcmPushService).sendToUser(eq(userId), eq("Review due"), eq("A card is ready."), any());
            verify(sesEmailService).sendToUser(userId, "Review due email", "<p>A card is ready.</p>");
            assertThat(notificationRepository.findAll())
                    .hasSize(2)
                    .allSatisfy(notification -> {
                        assertThat(notification.getNotificationType()).isEqualTo("AI_CARDS_READY");
                        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
                    });
        });
    }

    @Test
    void duplicateNotificationSendEvent_shouldDispatchOnlyOnce() {
        UUID userId = UUID.randomUUID();
        NotificationSend event = event(userId, List.of("FCM"));

        kafkaTemplate.send(TOPIC, event.getTenantId(), event);
        kafkaTemplate.send(TOPIC, event.getTenantId(), event);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            verify(fcmPushService, times(1))
                    .sendToUser(eq(userId), eq("Review due"), eq("A card is ready."), any());
            assertThat(notificationRepository.findAll()).hasSize(1);
        });
    }

    private static NotificationSend event(UUID userId, List<String> channels) {
        return NotificationSend.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setTenantId(UUID.randomUUID().toString())
                .setOccurredAt(Instant.now().toEpochMilli())
                .setTraceparent(null)
                .setUserId(userId.toString())
                .setNotificationType("AI_CARDS_READY")
                .setChannels(channels)
                .setTitle("Review due")
                .setBody("A card is ready.")
                .setEmailSubject("Review due email")
                .setEmailHtmlBody("<p>A card is ready.</p>")
                .setData(Map.of("deckId", "deck-1"))
                .build();
    }
}
