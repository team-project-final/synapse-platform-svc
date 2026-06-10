package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.synapse.platform.auth.api.TenantAnalyticsSnapshot;
import com.synapse.platform.auth.repository.TenantPlanCount;
import com.synapse.platform.auth.repository.TenantRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantAnalyticsServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Test
    void getTenantAnalytics_shouldMapStatusAndPlanCounts() {
        given(tenantRepository.countByDeletedAtIsNull()).willReturn(4L);
        given(tenantRepository.countByStatusAndDeletedAtIsNull("active")).willReturn(3L);
        given(tenantRepository.countByStatusAndDeletedAtIsNull("suspended")).willReturn(1L);
        given(tenantRepository.countByPlan()).willReturn(List.of(planCount("free", 3), planCount("pro", 1)));

        TenantAnalyticsSnapshot result = new TenantAnalyticsService(tenantRepository).getTenantAnalytics();

        assertThat(result.total()).isEqualTo(4);
        assertThat(result.active()).isEqualTo(3);
        assertThat(result.suspended()).isEqualTo(1);
        assertThat(result.plans()).containsEntry("free", 3L).containsEntry("pro", 1L);
    }

    private static TenantPlanCount planCount(String plan, long count) {
        return new TenantPlanCount() {
            @Override
            public String getPlan() {
                return plan;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }
}
