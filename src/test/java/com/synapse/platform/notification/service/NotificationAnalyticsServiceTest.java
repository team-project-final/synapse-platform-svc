package com.synapse.platform.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.synapse.platform.notification.api.NotificationAnalyticsSnapshot;
import com.synapse.platform.notification.entity.NotificationStatus;
import com.synapse.platform.notification.repository.NotificationRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationAnalyticsServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void getNotificationAnalytics_shouldCountTodaySentAndFailedNotifications() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-10T12:00:00+09:00");
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-06-10T00:00:00+09:00");
        given(notificationRepository.countByStatusAndSentAtGreaterThanEqual(
                NotificationStatus.SENT,
                dayStart.toInstant()))
                .willReturn(12L);
        given(notificationRepository.countByStatusAndCreatedAtGreaterThanEqual(
                NotificationStatus.FAILED,
                dayStart.toInstant()))
                .willReturn(1L);

        NotificationAnalyticsSnapshot result = new NotificationAnalyticsService(notificationRepository)
                .getNotificationAnalytics(now);

        assertThat(result.sentToday()).isEqualTo(12);
        assertThat(result.failedToday()).isEqualTo(1);
    }
}
