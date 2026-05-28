package com.synapse.platform.notification.service;

import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.repository.NotificationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int DAILY_EMAIL_LIMIT = 10;

    private final NotificationRepository notificationRepository;
    private final ObjectProvider<FcmPushService> fcmPushServiceProvider;
    private final ObjectProvider<SesEmailService> sesEmailServiceProvider;

    public NotificationService(
            NotificationRepository notificationRepository,
            ObjectProvider<FcmPushService> fcmPushServiceProvider,
            ObjectProvider<SesEmailService> sesEmailServiceProvider) {
        this.notificationRepository = notificationRepository;
        this.fcmPushServiceProvider = fcmPushServiceProvider;
        this.sesEmailServiceProvider = sesEmailServiceProvider;
    }

    @Transactional(noRollbackFor = RuntimeException.class)
    public void processNotificationSend(GenericRecord envelope) {
        UUID eventId = UUID.fromString(envelope.get("id").toString());
        UUID tenantId = UUID.fromString(envelope.get("tenantid").toString());
        GenericRecord payload = PlatformAvroEvents.decodeNotificationSend(envelope);
        UUID userId = UUID.fromString(payload.get("userId").toString());
        String notificationType = payload.get("notificationType").toString();
        String title = payload.get("title").toString();
        String body = payload.get("body").toString();
        String emailSubject = getOptionalString(payload, "emailSubject");
        String emailHtmlBody = getOptionalString(payload, "emailHtmlBody");

        Iterable<?> channels = (Iterable<?>) payload.get("channels");
        for (Object rawChannel : channels) {
            NotificationChannel channel = NotificationChannel.valueOf(rawChannel.toString());
            sendToChannel(
                    eventId,
                    userId,
                    tenantId,
                    notificationType,
                    channel,
                    title,
                    body,
                    emailSubject,
                    emailHtmlBody,
                    payload);
        }
    }

    private void sendToChannel(
            UUID eventId,
            UUID userId,
            UUID tenantId,
            String notificationType,
            NotificationChannel channel,
            String title,
            String body,
            String emailSubject,
            String emailHtmlBody,
            GenericRecord payload) {
        FcmPushService fcmPushService = fcmPushServiceProvider.getIfAvailable();
        SesEmailService sesEmailService = sesEmailServiceProvider.getIfAvailable();
        if (channel == NotificationChannel.FCM && fcmPushService == null) {
            log.info("FCM channel not configured - skipping for user {}", userId);
            return;
        }
        if (channel == NotificationChannel.EMAIL) {
            if (sesEmailService == null) {
                log.info("SES channel not configured - skipping for user {}", userId);
                return;
            }
            if (isDailyEmailLimitExceeded(userId)) {
                log.warn("Daily email limit exceeded for user {}: eventId={}", userId, eventId);
                return;
            }
        }

        Notification notification = notificationRepository.findByEventIdAndChannel(eventId, channel)
                .orElseGet(() -> notificationRepository.save(Notification.create(
                        eventId,
                        userId,
                        tenantId,
                        notificationType,
                        channel,
                        title,
                        body)));

        if (notification.isSent()) {
            log.debug("Skip already-sent notification: eventId={} channel={}", eventId, channel);
            return;
        }

        notification.incrementAttempts();
        notificationRepository.save(notification);

        try {
            if (channel == NotificationChannel.FCM) {
                dispatchFcm(fcmPushService, userId, title, body, payload);
            } else if (channel == NotificationChannel.EMAIL) {
                dispatchEmail(sesEmailService, userId, emailSubject, emailHtmlBody);
            }
            notification.markSent();
            notificationRepository.save(notification);
        } catch (RuntimeException exception) {
            notification.markFailed(exception.getMessage());
            notificationRepository.save(notification);
            throw exception;
        }
    }

    private boolean isDailyEmailLimitExceeded(UUID userId) {
        Instant dayStart = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        return notificationRepository.countTodayEmailByUserId(userId, dayStart) >= DAILY_EMAIL_LIMIT;
    }

    private void dispatchFcm(
            FcmPushService fcmPushService,
            UUID userId,
            String title,
            String body,
            GenericRecord payload) {
        fcmPushService.sendToUser(userId, title, body, data(payload));
    }

    private void dispatchEmail(
            SesEmailService sesEmailService,
            UUID userId,
            String subject,
            String htmlBody) {
        sesEmailService.sendToUser(
                userId,
                subject != null ? subject : "Synapse Notification",
                htmlBody != null ? htmlBody : "");
    }

    private Map<String, String> data(GenericRecord payload) {
        Map<String, String> data = new HashMap<>();
        Object rawData = payload.get("data");
        if (rawData instanceof Map<?, ?> map) {
            map.forEach((key, value) -> data.put(key.toString(), value.toString()));
        }
        return data;
    }

    private String getOptionalString(GenericRecord record, String field) {
        Object value = record.get(field);
        return value != null ? value.toString() : null;
    }
}
