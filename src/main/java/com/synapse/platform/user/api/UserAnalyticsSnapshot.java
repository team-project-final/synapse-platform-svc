package com.synapse.platform.user.api;

public record UserAnalyticsSnapshot(
        long total,
        long active,
        long suspended,
        long deleted,
        long newToday,
        long dau,
        long mau,
        String activitySource) {
}
