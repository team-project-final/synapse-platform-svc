package com.synapse.platform.auth.service;

import com.synapse.platform.auth.api.AdminTenantInfo;
import com.synapse.platform.auth.api.TenantAdminApi;
import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAdminService implements TenantAdminApi {

    private final TenantRepository tenantRepository;

    public TenantAdminService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AdminTenantInfo> listTenants(Pageable pageable) {
        return tenantRepository.findAllByDeletedAtIsNull(pageable)
                .map(this::toInfo);
    }

    @Override
    @Transactional
    public void changeStatus(UUID tenantId, String status) {
        Tenant tenant = tenantRepository.findByIdAndDeletedAtIsNull(tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Tenant not found"));
        if ("suspended".equals(status)) {
            tenant.suspend();
            return;
        }
        if ("active".equals(status)) {
            tenant.activate();
            return;
        }
        throw new IllegalArgumentException("Unsupported tenant status");
    }

    private AdminTenantInfo toInfo(Tenant tenant) {
        return new AdminTenantInfo(
                tenant.getId(),
                tenant.getName(),
                tenant.getSlug(),
                tenant.getPlan(),
                tenant.getStatus(),
                tenant.getCreatedAt());
    }
}
