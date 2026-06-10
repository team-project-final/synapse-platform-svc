package com.synapse.platform.auth.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantApi {
    Optional<TenantInfo> findById(UUID tenantId);

    Optional<PlanQuotaInfo> findPlanQuota(String planCode);

    List<PlanQuotaInfo> listPlanQuotas();

    void activatePlan(UUID tenantId, String planCode);
}
