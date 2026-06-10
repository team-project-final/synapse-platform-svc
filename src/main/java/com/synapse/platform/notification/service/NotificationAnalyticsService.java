package com.synapse.platform.notification.service;

import com.synapse.platform.notification.api.NotificationAnalyticsApi;
import com.synapse.platform.notification.api.NotificationAnalyticsSnapshot;
import com.synapse.platform.notification.entity.NotificationStatus;
import com.synapse.platform.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationAnalyticsService implements NotificationAnalyticsApi {

    private final NotificationRepository notificationRepository;

    public NotificationAnalyticsService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationAnalyticsSnapshot getNotificationAnalytics(OffsetDateTime now) {
        OffsetDateTime dayStart = startOfDay(now);
        return new NotificationAnalyticsSnapshot(
                notificationRepository.countByStatusAndSentAtGreaterThanEqual(
                        NotificationStatus.SENT,
                        dayStart.toInstant()),
                notificationRepository.countByStatusAndCreatedAtGreaterThanEqual(
                        NotificationStatus.FAILED,
                        dayStart.toInstant()));
    }

    private OffsetDateTime startOfDay(OffsetDateTime now) {
        return now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
    }
}
