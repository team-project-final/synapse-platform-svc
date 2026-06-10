package com.synapse.platform.audit.api;

import java.util.List;

public record AuditAnalyticsSnapshot(
        long activitiesToday,
        List<RecentAuditActivity> recentActivities) {

    public AuditAnalyticsSnapshot {
        recentActivities = List.copyOf(recentActivities);
    }
}
