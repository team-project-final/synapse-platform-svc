package com.synapse.platform.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AdminAnalyticsSummaryResponse(
        OffsetDateTime generatedAt,
        UsersSummary users,
        TenantsSummary tenants,
        List<UsageItem> usage,
        List<PendingItem> pendingItems,
        List<RecentActivity> recentActivities) {

    public AdminAnalyticsSummaryResponse {
        usage = List.copyOf(usage);
        pendingItems = List.copyOf(pendingItems);
        recentActivities = List.copyOf(recentActivities);
    }

    public record UsersSummary(
            long total,
            long active,
            long suspended,
            long deleted,
            long newToday,
            long dau,
            long mau,
            String activitySource) {
    }

    public record TenantsSummary(
            long total,
            long active,
            long suspended,
            Map<String, Long> plans) {

        public TenantsSummary {
            plans = Map.copyOf(plans);
        }
    }

    public record UsageItem(
            String key,
            String label,
            Long value,
            String unit,
            String status,
            String source) {
    }

    public record PendingItem(
            String key,
            String label,
            Long count,
            String severity,
            String status) {
    }

    public record RecentActivity(
            UUID id,
            String action,
            UUID userId,
            String resourceType,
            String resourceId,
            OffsetDateTime createdAt) {
    }
}
