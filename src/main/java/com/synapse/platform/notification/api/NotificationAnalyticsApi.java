package com.synapse.platform.notification.api;

import java.time.OffsetDateTime;

public interface NotificationAnalyticsApi {

    NotificationAnalyticsSnapshot getNotificationAnalytics(OffsetDateTime now);
}
