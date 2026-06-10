package com.synapse.platform.audit.api;

import java.time.OffsetDateTime;
import java.util.List;

public interface AuditAnalyticsApi {

    AuditAnalyticsSnapshot getAuditAnalytics(OffsetDateTime now, int recentActivitySize);

    List<RecentAuditActivity> findRecentActivities(int size);
}
