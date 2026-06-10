package com.synapse.platform.notification.dto.response;

import com.synapse.platform.notification.entity.Notification;
import java.time.Instant;
import java.util.UUID;

public record NotificationItemResponse(
        UUID id,
        String type,
        String title,
        String body,
        boolean read,
        Instant readAt,
        Instant createdAt,
        Instant sentAt
) {

    public static NotificationItemResponse from(Notification notification) {
        return new NotificationItemResponse(
                notification.getId(),
                notification.getNotificationType(),
                notification.getTitle(),
                notification.getBody(),
                notification.isRead(),
                notification.getReadAt(),
                notification.getCreatedAt(),
                notification.getSentAt());
    }
}
