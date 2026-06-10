package com.synapse.platform.admin.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminSettingsResponse(
        List<PlanQuotaItem> planQuotas,
        List<FeatureFlagItem> featureFlags,
        RateLimitSettings rateLimit,
        OffsetDateTime updatedAt
) {

    public AdminSettingsResponse {
        planQuotas = List.copyOf(planQuotas);
        featureFlags = List.copyOf(featureFlags);
    }

    public record PlanQuotaItem(
            String planCode,
            String displayName,
            Integer maxNotes,
            Integer maxCards,
            Long maxStorageBytes,
            Long maxAiTokensMonthly,
            Integer maxAiCardGenerationsMonthly,
            Integer maxUsersPerTenant
    ) {
    }

    public record FeatureFlagItem(
            String key,
            String label,
            boolean enabled
    ) {
    }

    public record RateLimitSettings(
            int apiRequestsPerMinute
    ) {
    }
}
