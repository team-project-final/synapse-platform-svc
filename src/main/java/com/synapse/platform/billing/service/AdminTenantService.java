package com.synapse.platform.billing.service;

import com.synapse.platform.auth.api.TenantAdminApi;
import com.synapse.platform.billing.dto.response.AdminTenantResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminTenantService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TenantAdminApi tenantAdminApi;

    public AdminTenantService(TenantAdminApi tenantAdminApi) {
        this.tenantAdminApi = tenantAdminApi;
    }

    @Transactional(readOnly = true)
    public Page<AdminTenantResponse> listTenants(int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(0, page),
                Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        return tenantAdminApi.listTenants(pageable)
                .map(AdminTenantResponse::from);
    }

    @Transactional
    public void changeTenantStatus(UUID tenantId, String status) {
        tenantAdminApi.changeStatus(tenantId, status);
    }
}
