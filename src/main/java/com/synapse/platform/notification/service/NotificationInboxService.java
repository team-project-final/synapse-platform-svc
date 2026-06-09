package com.synapse.platform.notification.service;

import com.synapse.platform.notification.dto.response.NotificationItemResponse;
import com.synapse.platform.notification.dto.response.NotificationPageResponse;
import com.synapse.platform.notification.dto.response.NotificationReadAllResponse;
import com.synapse.platform.notification.dto.response.NotificationUnreadCountResponse;
import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.entity.NotificationStatus;
import com.synapse.platform.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationInboxService {

    private static final NotificationChannel INBOX_CHANNEL = NotificationChannel.FCM;
    private static final NotificationStatus INBOX_STATUS = NotificationStatus.SENT;

    private final NotificationRepository notificationRepository;

    public NotificationInboxService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(UUID userId, Pageable pageable) {
        return NotificationPageResponse.from(notificationRepository.findByUserIdAndChannelAndStatus(
                userId,
                INBOX_CHANNEL,
                INBOX_STATUS,
                pageable));
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse countUnread(UUID userId) {
        long count = notificationRepository.countByUserIdAndChannelAndStatusAndReadAtIsNull(
                userId,
                INBOX_CHANNEL,
                INBOX_STATUS);
        return new NotificationUnreadCountResponse(count);
    }

    @Transactional
    public NotificationItemResponse markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserIdAndChannelAndStatus(
                        notificationId,
                        userId,
                        INBOX_CHANNEL,
                        INBOX_STATUS)
                .orElseThrow(() -> new EntityNotFoundException("Notification not found"));
        notification.markRead(Instant.now());
        return NotificationItemResponse.from(notification);
    }

    @Transactional
    public NotificationReadAllResponse markAllRead(UUID userId) {
        int updatedCount = notificationRepository.markAllRead(
                userId,
                INBOX_CHANNEL,
                INBOX_STATUS,
                Instant.now());
        return new NotificationReadAllResponse(updatedCount);
    }
}
