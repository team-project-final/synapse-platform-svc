package com.synapse.platform.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.synapse.platform.admin.dto.AdminSettingsResponse;
import com.synapse.platform.admin.dto.AdminSettingsUpdateRequest;
import com.synapse.platform.admin.dto.AdminSettingsUpdateRequest.FeatureFlagUpdate;
import com.synapse.platform.admin.dto.AdminSettingsUpdateRequest.RateLimitUpdate;
import com.synapse.platform.admin.entity.AdminSetting;
import com.synapse.platform.admin.repository.AdminSettingRepository;
import com.synapse.platform.auth.api.PlanQuotaInfo;
import com.synapse.platform.auth.api.TenantApi;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminSettingsServiceTest {

    @Mock
    private TenantApi tenantApi;

    @Mock
    private AdminSettingRepository adminSettingRepository;

    @Test
    void getSettings_shouldReturnDefaultSettingsAndPlanQuotas() {
        List<PlanQuotaInfo> quotas = List.of(planQuota("pro"), planQuota("free"));
        given(tenantApi.listPlanQuotas()).willReturn(quotas);
        given(adminSettingRepository.findAll()).willReturn(List.of());

        AdminSettingsResponse result = service().getSettings();

        assertThat(result.planQuotas())
                .extracting(AdminSettingsResponse.PlanQuotaItem::planCode)
                .containsExactly("free", "pro");
        assertThat(result.featureFlags())
                .extracting(AdminSettingsResponse.FeatureFlagItem::key)
                .containsExactly(
                        "aiCardAutoGeneration",
                        "googleSocialLogin",
                        "githubSocialLogin",
                        "realtimeCollaborativeEditing",
                        "voiceReviewBeta");
        assertThat(result.featureFlags())
                .filteredOn(flag -> flag.key().equals("githubSocialLogin"))
                .extracting(AdminSettingsResponse.FeatureFlagItem::enabled)
                .containsExactly(false);
        assertThat(result.rateLimit().apiRequestsPerMinute()).isEqualTo(100);
        assertThat(result.updatedAt()).isNull();
    }

    @Test
    void getSettings_shouldPreferPersistedSettings() {
        List<PlanQuotaInfo> quotas = List.of(planQuota("free"));
        given(tenantApi.listPlanQuotas()).willReturn(quotas);
        given(adminSettingRepository.findAll()).willReturn(List.of(
                AdminSetting.create("feature.githubSocialLogin", "true"),
                AdminSetting.create("rateLimit.apiRequestsPerMinute", "250")));

        AdminSettingsResponse result = service().getSettings();

        assertThat(result.featureFlags())
                .filteredOn(flag -> flag.key().equals("githubSocialLogin"))
                .extracting(AdminSettingsResponse.FeatureFlagItem::enabled)
                .containsExactly(true);
        assertThat(result.rateLimit().apiRequestsPerMinute()).isEqualTo(250);
        assertThat(result.updatedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void updateSettings_shouldPersistFeatureFlagsAndRateLimit() {
        List<PlanQuotaInfo> quotas = List.of(planQuota("free"));
        given(tenantApi.listPlanQuotas()).willReturn(quotas);
        given(adminSettingRepository.findAll()).willReturn(List.of(
                AdminSetting.create("feature.githubSocialLogin", "false")));
        AdminSettingsUpdateRequest request = new AdminSettingsUpdateRequest(
                List.of(new FeatureFlagUpdate("githubSocialLogin", true)),
                new RateLimitUpdate(300));

        AdminSettingsResponse result = service().updateSettings(request);

        ArgumentCaptor<Iterable<AdminSetting>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(adminSettingRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(AdminSetting::getSettingKey)
                .contains("feature.githubSocialLogin", "rateLimit.apiRequestsPerMinute");
        assertThat(result.featureFlags())
                .filteredOn(flag -> flag.key().equals("githubSocialLogin"))
                .extracting(AdminSettingsResponse.FeatureFlagItem::enabled)
                .containsExactly(true);
        assertThat(result.rateLimit().apiRequestsPerMinute()).isEqualTo(300);
    }

    @Test
    void updateSettings_unknownFeatureFlag_shouldThrowBadRequest() {
        given(adminSettingRepository.findAll()).willReturn(List.of());
        AdminSettingsUpdateRequest request = new AdminSettingsUpdateRequest(
                List.of(new FeatureFlagUpdate("unknown", true)),
                new RateLimitUpdate(100));

        assertThatThrownBy(() -> service().updateSettings(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unknown feature flag key");
    }

    @Test
    void updateSettings_duplicateFeatureFlag_shouldThrowBadRequest() {
        given(adminSettingRepository.findAll()).willReturn(List.of());
        AdminSettingsUpdateRequest request = new AdminSettingsUpdateRequest(
                List.of(
                        new FeatureFlagUpdate("githubSocialLogin", true),
                        new FeatureFlagUpdate("githubSocialLogin", false)),
                new RateLimitUpdate(100));

        assertThatThrownBy(() -> service().updateSettings(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Duplicated feature flag key");
    }

    private AdminSettingsService service() {
        return new AdminSettingsService(tenantApi, adminSettingRepository);
    }

    private PlanQuotaInfo planQuota(String plan) {
        return new PlanQuotaInfo(
                plan,
                plan.substring(0, 1).toUpperCase() + plan.substring(1),
                1000,
                500,
                100000000L,
                100000L,
                10,
                1);
    }
}
