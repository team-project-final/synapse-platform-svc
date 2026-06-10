package com.synapse.platform.auth.api;

import java.util.Map;

public record TenantAnalyticsSnapshot(
        long total,
        long active,
        long suspended,
        Map<String, Long> plans) {

    public TenantAnalyticsSnapshot {
        plans = Map.copyOf(plans);
    }
}
