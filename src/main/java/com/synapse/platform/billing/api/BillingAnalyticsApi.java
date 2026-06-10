package com.synapse.platform.billing.api;

import java.time.OffsetDateTime;

public interface BillingAnalyticsApi {

    BillingAnalyticsSnapshot getBillingAnalytics(OffsetDateTime now);
}
