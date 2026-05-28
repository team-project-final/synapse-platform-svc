package com.synapse.platform.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.entity.NotificationStatus;
import com.synapse.platform.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private FcmPushService fcmPushService;

    @Mock
    private SesEmailService sesEmailService;

    @Mock
    private ObjectProvider<FcmPushService> fcmPushServiceProvider;

    @Mock
    private ObjectProvider<SesEmailService> sesEmailServiceProvider;

    @Test
    void processNotificationSend_fcmChannel_shouldSendPushAndMarkSent() {
        UUID userId = UUID.randomUUID();
        GenericRecord envelope = envelope(userId, List.of("FCM"));
        given(fcmPushServiceProvider.getIfAvailable()).willReturn(fcmPushService);
        given(notificationRepository.findByEventIdAndChannel(
                UUID.fromString(envelope.get("id").toString()),
                NotificationChannel.FCM))
                .willReturn(Optional.empty());
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> invocation.getArgument(0));

        service().processNotificationSend(envelope);

        verify(fcmPushService).sendToUser(eq(userId), eq("Review due"), eq("A card is ready."), any(Map.class));
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, Mockito.atLeastOnce()).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notificationCaptor.getValue().getChannel()).isEqualTo(NotificationChannel.FCM);
    }

    @Test
    void processNotificationSend_emailChannel_shouldSendEmailAndMarkSent() {
        UUID userId = UUID.randomUUID();
        GenericRecord envelope = envelope(userId, List.of("EMAIL"));
        given(sesEmailServiceProvider.getIfAvailable()).willReturn(sesEmailService);
        given(notificationRepository.findByEventIdAndChannel(
                UUID.fromString(envelope.get("id").toString()),
                NotificationChannel.EMAIL))
                .willReturn(Optional.empty());
        given(notificationRepository.countTodayEmailByUserId(eq(userId), any(Instant.class))).willReturn(0L);
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> invocation.getArgument(0));

        service().processNotificationSend(envelope);

        verify(sesEmailService).sendToUser(userId, "Review due email", "<p>A card is ready.</p>");
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, Mockito.atLeastOnce()).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notificationCaptor.getValue().getChannel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    void processNotificationSend_existingSentNotification_shouldSkipSend() {
        UUID userId = UUID.randomUUID();
        GenericRecord envelope = envelope(userId, List.of("FCM"));
        Notification existing = notification(
                UUID.fromString(envelope.get("id").toString()),
                userId,
                NotificationChannel.FCM);
        existing.markSent();
        given(fcmPushServiceProvider.getIfAvailable()).willReturn(fcmPushService);
        given(notificationRepository.findByEventIdAndChannel(existing.getEventId(), NotificationChannel.FCM))
                .willReturn(Optional.of(existing));

        service().processNotificationSend(envelope);

        verify(fcmPushService, never()).sendToUser(any(), any(), any(), any());
        verify(notificationRepository, never()).save(existing);
    }

    @Test
    void processNotificationSend_fcmFailure_shouldMarkFailedAndRethrow() {
        UUID userId = UUID.randomUUID();
        GenericRecord envelope = envelope(userId, List.of("FCM"));
        List<Notification> saved = new ArrayList<>();
        given(fcmPushServiceProvider.getIfAvailable()).willReturn(fcmPushService);
        given(notificationRepository.findByEventIdAndChannel(
                UUID.fromString(envelope.get("id").toString()),
                NotificationChannel.FCM))
                .willReturn(Optional.empty());
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            saved.add(notification);
            return notification;
        });
        willThrow(new RuntimeException("fcm down"))
                .given(fcmPushService)
                .sendToUser(eq(userId), any(), any(), any());

        assertThatThrownBy(() -> service().processNotificationSend(envelope))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("fcm down");

        assertThat(saved).isNotEmpty();
        assertThat(saved.get(saved.size() - 1).getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(saved.get(saved.size() - 1).getErrorMessage()).contains("fcm down");
    }

    @Test
    void processNotificationSend_emailDailyLimitExceeded_shouldSkipWithoutSavingSentRecord() {
        UUID userId = UUID.randomUUID();
        GenericRecord envelope = envelope(userId, List.of("EMAIL"));
        given(sesEmailServiceProvider.getIfAvailable()).willReturn(sesEmailService);
        given(notificationRepository.countTodayEmailByUserId(eq(userId), any(Instant.class))).willReturn(10L);

        service().processNotificationSend(envelope);

        verify(sesEmailService, never()).sendToUser(any(), any(), any());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void processNotificationSend_disabledFcmChannel_shouldSkipWithoutSavingRecord() {
        UUID userId = UUID.randomUUID();
        GenericRecord envelope = envelope(userId, List.of("FCM"));
        given(fcmPushServiceProvider.getIfAvailable()).willReturn(null);

        service().processNotificationSend(envelope);

        verify(fcmPushService, never()).sendToUser(any(), any(), any(), any());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    private NotificationService service() {
        return new NotificationService(notificationRepository, fcmPushServiceProvider, sesEmailServiceProvider);
    }

    private static GenericRecord envelope(UUID userId, List<String> channels) {
        return PlatformAvroEvents.notificationSendEnvelope(
                userId,
                UUID.randomUUID(),
                "CARD_REVIEW_DUE",
                channels,
                "Review due",
                "A card is ready.",
                "Review due email",
                "<p>A card is ready.</p>");
    }

    private static Notification notification(UUID eventId, UUID userId, NotificationChannel channel) {
        return Notification.create(
                eventId,
                userId,
                UUID.randomUUID(),
                "CARD_REVIEW_DUE",
                channel,
                "Review due",
                "A card is ready.");
    }
}
