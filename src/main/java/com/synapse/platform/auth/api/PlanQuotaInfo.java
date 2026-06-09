package com.synapse.platform.auth.api;

public record PlanQuotaInfo(
        String plan,
        String displayName,
        Integer maxNotes,
        Integer maxCards,
        Long maxStorageBytes,
        Long maxAiTokensMonthly,
        Integer maxAiCardGenerationsMonthly,
        Integer maxUsersPerTenant
) {
}
