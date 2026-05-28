package com.synapse.platform.auth.api;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TenantAdminApi {

    Page<AdminTenantInfo> listTenants(Pageable pageable);

    void changeStatus(UUID tenantId, String status);
}
