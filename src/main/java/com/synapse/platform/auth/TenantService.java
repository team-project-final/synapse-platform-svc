package com.synapse.platform.auth;

import com.synapse.platform.auth.api.TenantApi;
import com.synapse.platform.auth.api.TenantInfo;
import com.synapse.platform.auth.repository.TenantRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantService implements TenantApi {

    private final TenantRepository tenantRepository;

    public TenantService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Optional<TenantInfo> findById(UUID tenantId) {
        return tenantRepository.findById(tenantId)
                .map(tenant -> new TenantInfo(tenant.getId(), tenant.getPlan(), tenant.getStatus()));
    }

    @Override
    @Transactional
    public void activatePlan(UUID tenantId, String planCode) {
        tenantRepository.findById(tenantId).ifPresent(tenant -> {
            tenant.activatePlan(planCode);
            tenantRepository.save(tenant);
        });
    }
}
