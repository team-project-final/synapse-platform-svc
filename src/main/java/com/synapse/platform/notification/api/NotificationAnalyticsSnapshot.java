package com.synapse.platform.notification.api;

public record NotificationAnalyticsSnapshot(
        long sentToday,
        long failedToday) {
}
