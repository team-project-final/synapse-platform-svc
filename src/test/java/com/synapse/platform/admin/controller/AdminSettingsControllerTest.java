package com.synapse.platform.admin.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.admin.dto.AdminSettingsResponse;
import com.synapse.platform.admin.dto.AdminSettingsResponse.FeatureFlagItem;
import com.synapse.platform.admin.dto.AdminSettingsResponse.PlanQuotaItem;
import com.synapse.platform.admin.dto.AdminSettingsResponse.RateLimitSettings;
import com.synapse.platform.admin.dto.AdminSettingsUpdateRequest;
import com.synapse.platform.admin.service.AdminSettingsService;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminSettingsControllerTest {

    private final AdminSettingsService adminSettingsService = org.mockito.Mockito.mock(AdminSettingsService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminSettingsController(adminSettingsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getSettings_shouldReturnAdminSettings() throws Exception {
        given(adminSettingsService.getSettings()).willReturn(settings());

        mockMvc.perform(get("/api/v1/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planQuotas[0].planCode").value("free"))
                .andExpect(jsonPath("$.featureFlags[0].key").value("aiCardAutoGeneration"))
                .andExpect(jsonPath("$.featureFlags[0].enabled").value(true))
                .andExpect(jsonPath("$.rateLimit.apiRequestsPerMinute").value(100))
                .andExpect(jsonPath("$.updatedAt").exists());

        verify(adminSettingsService).getSettings();
    }

    @Test
    void updateSettings_shouldReturnUpdatedAdminSettings() throws Exception {
        given(adminSettingsService.updateSettings(org.mockito.ArgumentMatchers.any(AdminSettingsUpdateRequest.class)))
                .willReturn(settings());

        mockMvc.perform(put("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "featureFlags": [
                                    {"key": "githubSocialLogin", "enabled": true}
                                  ],
                                  "rateLimit": {"apiRequestsPerMinute": 100}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateLimit.apiRequestsPerMinute").value(100));

        verify(adminSettingsService)
                .updateSettings(org.mockito.ArgumentMatchers.any(AdminSettingsUpdateRequest.class));
    }

    @Test
    void updateSettings_invalidRateLimit_shouldReturnBadRequest() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "featureFlags": [],
                                  "rateLimit": {"apiRequestsPerMinute": 0}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAT-001"));
    }

    private static AdminSettingsResponse settings() {
        return new AdminSettingsResponse(
                List.of(new PlanQuotaItem(
                        "free",
                        "Free",
                        1000,
                        500,
                        100000000L,
                        100000L,
                        10,
                        1)),
                List.of(new FeatureFlagItem("aiCardAutoGeneration", "AI 카드 자동 생성", true)),
                new RateLimitSettings(100),
                OffsetDateTime.parse("2026-06-10T10:00:00Z"));
    }
}
