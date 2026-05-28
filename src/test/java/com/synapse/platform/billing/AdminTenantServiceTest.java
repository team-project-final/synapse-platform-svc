package com.synapse.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.api.AdminTenantInfo;
import com.synapse.platform.auth.api.TenantAdminApi;
import com.synapse.platform.billing.service.AdminTenantService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

class AdminTenantServiceTest {

    private final TenantAdminApi tenantAdminApi = mock(TenantAdminApi.class);

    @Test
    void listTenants_shouldClampPageSizeAndMapResponses() {
        UUID tenantId = UUID.randomUUID();
        given(tenantAdminApi.listTenants(any()))
                .willReturn(new PageImpl<>(java.util.List.of(new AdminTenantInfo(
                        tenantId,
                        "Acme",
                        "acme",
                        "free",
                        "active",
                        OffsetDateTime.parse("2026-05-28T00:00:00Z")))));

        var result = service().listTenants(-1, 1000);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(tenantId);
    }

    @Test
    void changeTenantStatus_shouldDelegateToAuthApi() {
        UUID tenantId = UUID.randomUUID();

        service().changeTenantStatus(tenantId, "suspended");

        verify(tenantAdminApi).changeStatus(tenantId, "suspended");
    }

    private AdminTenantService service() {
        return new AdminTenantService(tenantAdminApi);
    }
}
