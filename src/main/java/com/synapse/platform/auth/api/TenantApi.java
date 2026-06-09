package com.synapse.platform.auth.api;

import java.util.Optional;
import java.util.UUID;

public interface TenantApi {
    Optional<TenantInfo> findById(UUID tenantId);

    Optional<PlanQuotaInfo> findPlanQuota(String planCode);

    void activatePlan(UUID tenantId, String planCode);
}
