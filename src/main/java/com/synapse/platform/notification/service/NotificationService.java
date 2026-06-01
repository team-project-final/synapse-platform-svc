package com.synapse.platform.notification.service;

import com.synapse.platform.NotificationSend;
import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.repository.NotificationRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
    public void processNotificationSend(NotificationSend event) {
        UUID eventId = UUID.fromString(event.getEventId());
        UUID tenantId = UUID.fromString(event.getTenantId());
        UUID userId = UUID.fromString(event.getUserId());
        String notificationType = event.getNotificationType();
        String title = event.getTitle();
        String body = event.getBody();
        String emailSubject = event.getEmailSubject();
        String emailHtmlBody = event.getEmailHtmlBody();
        Map<String, String> data = data(event.getData());

        for (String rawChannel : event.getChannels()) {
            NotificationChannel channel = NotificationChannel.valueOf(rawChannel);
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
                    data);
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
            Map<String, String> data) {
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
                dispatchFcm(fcmPushService, userId, title, body, data);
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
            Map<String, String> data) {
        fcmPushService.sendToUser(userId, title, body, data);
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

    private Map<String, String> data(Map<String, String> payloadData) {
        Map<String, String> data = new HashMap<>();
        if (payloadData != null) {
            payloadData.forEach((key, value) -> data.put(key, value));
        }
        return data;
    }
}
