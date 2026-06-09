package com.synapse.platform.billing.dto.response;

import com.synapse.platform.auth.api.PlanQuotaInfo;
import com.synapse.platform.billing.entity.Subscription;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BillingUsageResponse(
        UUID tenantId,
        String planCode,
        String subscriptionStatus,
        OffsetDateTime currentPeriodStart,
        OffsetDateTime currentPeriodEnd,
        Quotas quotas,
        Usage usage
) {

    private static final String SOURCE_NOT_CONNECTED = "NOT_CONNECTED";

    public static BillingUsageResponse of(
            UUID tenantId,
            String planCode,
            Subscription subscription,
            PlanQuotaInfo quota) {
        Quotas quotas = Quotas.from(quota);
        return new BillingUsageResponse(
                tenantId,
                planCode,
                subscription == null ? null : subscription.getStatus().name(),
                subscription == null ? null : subscription.getCurrentPeriodStart(),
                subscription == null ? null : subscription.getCurrentPeriodEnd(),
                quotas,
                Usage.from(quotas));
    }

    public record Quotas(
            Integer maxNotes,
            Integer maxCards,
            Long maxStorageBytes,
            Long maxAiTokensMonthly,
            Integer maxAiCardGenerationsMonthly,
            Integer maxUsersPerTenant
    ) {

        static Quotas from(PlanQuotaInfo quota) {
            return new Quotas(
                    quota.maxNotes(),
                    quota.maxCards(),
                    quota.maxStorageBytes(),
                    quota.maxAiTokensMonthly(),
                    quota.maxAiCardGenerationsMonthly(),
                    quota.maxUsersPerTenant());
        }
    }

    public record Usage(
            UsageMetric notes,
            UsageMetric cards,
            UsageMetric storageBytes,
            UsageMetric aiTokensMonthly,
            UsageMetric aiCardGenerationsMonthly,
            UsageMetric users
    ) {

        static Usage from(Quotas quotas) {
            return new Usage(
                    UsageMetric.notConnected(toLong(quotas.maxNotes())),
                    UsageMetric.notConnected(toLong(quotas.maxCards())),
                    UsageMetric.notConnected(quotas.maxStorageBytes()),
                    UsageMetric.notConnected(quotas.maxAiTokensMonthly()),
                    UsageMetric.notConnected(toLong(quotas.maxAiCardGenerationsMonthly())),
                    UsageMetric.notConnected(toLong(quotas.maxUsersPerTenant())));
        }

        private static Long toLong(Integer value) {
            return value == null ? null : value.longValue();
        }
    }

    public record UsageMetric(
            Long used,
            Long limit,
            Long remaining,
            String source
    ) {

        static UsageMetric notConnected(Long limit) {
            return new UsageMetric(null, limit, null, SOURCE_NOT_CONNECTED);
        }
    }
}
