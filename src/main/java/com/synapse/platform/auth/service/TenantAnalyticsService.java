package com.synapse.platform.auth.service;

import com.synapse.platform.auth.api.TenantAnalyticsApi;
import com.synapse.platform.auth.api.TenantAnalyticsSnapshot;
import com.synapse.platform.auth.repository.TenantPlanCount;
import com.synapse.platform.auth.repository.TenantRepository;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAnalyticsService implements TenantAnalyticsApi {

    private static final String ACTIVE = "active";
    private static final String SUSPENDED = "suspended";

    private final TenantRepository tenantRepository;

    public TenantAnalyticsService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TenantAnalyticsSnapshot getTenantAnalytics() {
        Map<String, Long> plans = tenantRepository.countByPlan().stream()
                .collect(Collectors.toUnmodifiableMap(TenantPlanCount::getPlan, TenantPlanCount::getCount));
        return new TenantAnalyticsSnapshot(
                tenantRepository.countByDeletedAtIsNull(),
                tenantRepository.countByStatusAndDeletedAtIsNull(ACTIVE),
                tenantRepository.countByStatusAndDeletedAtIsNull(SUSPENDED),
                plans);
    }
}
