package com.synapse.platform.billing.api;

public record BillingAnalyticsSnapshot(
        long activeSubscriptions,
        long paidPaymentsToday,
        long revenueTodayMinorUnits) {
}
