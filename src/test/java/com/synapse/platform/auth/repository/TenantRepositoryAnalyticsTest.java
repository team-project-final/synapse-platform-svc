package com.synapse.platform.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.auth.entity.Tenant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TenantRepositoryAnalyticsTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Test
    void analyticsCounts_shouldExcludeDeletedTenantsAndGroupPlans() {
        long totalBaseline = tenantRepository.countByDeletedAtIsNull();
        long activeBaseline = tenantRepository.countByStatusAndDeletedAtIsNull("active");
        long suspendedBaseline = tenantRepository.countByStatusAndDeletedAtIsNull("suspended");
        Map<String, Long> planBaseline = planCounts();

        Tenant freeActive = tenant("free-active");
        Tenant proActive = tenant("pro-active");
        proActive.activatePlan("pro");

        Tenant proSuspended = tenant("pro-suspended");
        proSuspended.activatePlan("pro");
        proSuspended.suspend();

        Tenant deleted = tenant("deleted");
        ReflectionTestUtils.setField(deleted, "deletedAt", OffsetDateTime.parse("2026-06-10T00:00:00Z"));

        tenantRepository.saveAll(List.of(freeActive, proActive, proSuspended, deleted));
        tenantRepository.flush();

        Map<String, Long> plans = planCounts();

        assertThat(tenantRepository.countByDeletedAtIsNull()).isEqualTo(totalBaseline + 3);
        assertThat(tenantRepository.countByStatusAndDeletedAtIsNull("active")).isEqualTo(activeBaseline + 2);
        assertThat(tenantRepository.countByStatusAndDeletedAtIsNull("suspended")).isEqualTo(suspendedBaseline + 1);
        assertThat(plans.getOrDefault("free", 0L)).isEqualTo(planBaseline.getOrDefault("free", 0L) + 1);
        assertThat(plans.getOrDefault("pro", 0L)).isEqualTo(planBaseline.getOrDefault("pro", 0L) + 2);
    }

    private static Tenant tenant(String slugPrefix) {
        return Tenant.ofPersonal(slugPrefix, slugPrefix + "-" + UUID.randomUUID());
    }

    private Map<String, Long> planCounts() {
        return tenantRepository.countByPlan().stream()
                .collect(Collectors.toMap(TenantPlanCount::getPlan, TenantPlanCount::getCount));
    }
}
