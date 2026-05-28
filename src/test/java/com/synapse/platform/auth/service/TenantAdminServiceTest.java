package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.repository.TenantRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TenantAdminServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Test
    void listTenants_shouldMapAdminTenantInfo() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        given(tenantRepository.findAllByDeletedAtIsNull(any()))
                .willReturn(new PageImpl<>(java.util.List.of(tenant)));

        var result = service().listTenants(PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(tenantId);
        assertThat(result.getContent().getFirst().status()).isEqualTo("active");
    }

    @Test
    void changeStatus_suspended_shouldSuspendTenant() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        given(tenantRepository.findByIdAndDeletedAtIsNull(tenantId)).willReturn(Optional.of(tenant));

        service().changeStatus(tenantId, "suspended");

        assertThat(tenant.getStatus()).isEqualTo("suspended");
    }

    @Test
    void changeStatus_active_shouldActivateTenant() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        tenant.suspend();
        given(tenantRepository.findByIdAndDeletedAtIsNull(tenantId)).willReturn(Optional.of(tenant));

        service().changeStatus(tenantId, "active");

        assertThat(tenant.getStatus()).isEqualTo("active");
    }

    @Test
    void changeStatus_unknownTenant_shouldThrowNotFound() {
        UUID tenantId = UUID.randomUUID();
        given(tenantRepository.findByIdAndDeletedAtIsNull(tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().changeStatus(tenantId, "active"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void changeStatus_deletedTenant_shouldThrowNotFound() {
        UUID tenantId = UUID.randomUUID();
        given(tenantRepository.findByIdAndDeletedAtIsNull(tenantId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().changeStatus(tenantId, "suspended"))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void changeStatus_invalidStatus_shouldThrowBadRequest() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId);
        given(tenantRepository.findByIdAndDeletedAtIsNull(tenantId)).willReturn(Optional.of(tenant));

        assertThatThrownBy(() -> service().changeStatus(tenantId, "deleted"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TenantAdminService service() {
        return new TenantAdminService(tenantRepository);
    }

    private static Tenant tenant(UUID tenantId) {
        Tenant tenant = Tenant.ofPersonal("Acme", "acme");
        ReflectionTestUtils.setField(tenant, "id", tenantId);
        ReflectionTestUtils.setField(tenant, "createdAt", OffsetDateTime.parse("2026-05-28T00:00:00Z"));
        ReflectionTestUtils.setField(tenant, "updatedAt", OffsetDateTime.parse("2026-05-28T00:00:00Z"));
        return tenant;
    }
}
