package com.synapse.platform.auth.service;

import com.synapse.platform.auth.api.TenantApi;
import com.synapse.platform.auth.api.TenantInfo;
import com.synapse.platform.auth.api.PlanQuotaInfo;
import com.synapse.platform.auth.entity.PlanQuota;
import com.synapse.platform.auth.repository.PlanQuotaRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService implements TenantApi {

    private final TenantRepository tenantRepository;
    private final PlanQuotaRepository planQuotaRepository;

    public TenantService(TenantRepository tenantRepository, PlanQuotaRepository planQuotaRepository) {
        this.tenantRepository = tenantRepository;
        this.planQuotaRepository = planQuotaRepository;
    }

    @Override
    public Optional<TenantInfo> findById(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> new TenantInfo(tenant.getId(), tenant.getPlan(), tenant.getStatus()));
    }

    @Override
    public Optional<PlanQuotaInfo> findPlanQuota(String planCode) {
        if (planCode == null || planCode.isBlank()) {
            return Optional.empty();
        }
        return planQuotaRepository.findById(planCode.toLowerCase(Locale.ROOT))
                .map(this::toPlanQuotaInfo);
    }

    @Override
    @Transactional
    public void activatePlan(UUID tenantId, String planCode) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.activatePlan(planCode);
            tenantRepository.save(tenant);
        });
    }

    private PlanQuotaInfo toPlanQuotaInfo(PlanQuota planQuota) {
        return new PlanQuotaInfo(
                planQuota.getPlan(),
                planQuota.getDisplayName(),
                planQuota.getMaxNotes(),
                planQuota.getMaxCards(),
                planQuota.getMaxStorageBytes(),
                planQuota.getMaxAiTokensMonthly(),
                planQuota.getMaxAiCardGenerationsMonthly(),
                planQuota.getMaxUsersPerTenant());
    }
}
