package com.synapse.platform.billing.dto.response;

import com.synapse.platform.auth.api.AdminTenantInfo;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminTenantResponse(
        UUID id,
        String name,
        String slug,
        String plan,
        String status,
        OffsetDateTime createdAt) {

    public static AdminTenantResponse from(AdminTenantInfo tenant) {
        return new AdminTenantResponse(
                tenant.id(),
                tenant.name(),
                tenant.slug(),
                tenant.plan(),
                tenant.status(),
                tenant.createdAt());
    }
}
