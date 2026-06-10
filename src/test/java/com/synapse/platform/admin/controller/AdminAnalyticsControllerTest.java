package com.synapse.platform.admin.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.PendingItem;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.RecentActivity;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.TenantsSummary;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.UsageItem;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.UsersSummary;
import com.synapse.platform.admin.service.AdminAnalyticsService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminAnalyticsControllerTest {

    private final AdminAnalyticsService adminAnalyticsService = org.mockito.Mockito.mock(AdminAnalyticsService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminAnalyticsController(adminAnalyticsService))
                .build();
    }

    @Test
    void getSummary_shouldReturnAnalyticsSummary() throws Exception {
        given(adminAnalyticsService.getSummary()).willReturn(summary());

        mockMvc.perform(get("/api/v1/admin/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").value(10))
                .andExpect(jsonPath("$.users.activitySource").value("USERS_LAST_LOGIN_AT"))
                .andExpect(jsonPath("$.tenants.plans.free").value(3))
                .andExpect(jsonPath("$.usage[0].key").value("notifications.sent.today"))
                .andExpect(jsonPath("$.usage[1].status").value("NOT_CONNECTED"))
                .andExpect(jsonPath("$.pendingItems[0].status").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.recentActivities[0].action").value("USER_LOGIN"));

        verify(adminAnalyticsService).getSummary();
    }

    private static AdminAnalyticsSummaryResponse summary() {
        UUID userId = UUID.randomUUID();
        return new AdminAnalyticsSummaryResponse(
                OffsetDateTime.parse("2026-06-10T10:00:00+09:00"),
                new UsersSummary(10, 8, 1, 1, 2, 3, 7, "USERS_LAST_LOGIN_AT"),
                new TenantsSummary(4, 3, 1, Map.of("free", 3L, "pro", 1L)),
                List.of(
                        new UsageItem("notifications.sent.today", "오늘 발송 알림", 12L, "count", "OK", "notifications"),
                        new UsageItem("ai.tokens.monthly", "AI 토큰", null, "tokens", "NOT_CONNECTED", "learning-ai")),
                List.of(new PendingItem("data-requests", "GDPR 요청", null, "INFO", "NOT_IMPLEMENTED")),
                List.of(new RecentActivity(
                        UUID.randomUUID(),
                        "USER_LOGIN",
                        userId,
                        "USER",
                        userId.toString(),
                        OffsetDateTime.parse("2026-06-10T09:50:00+09:00"))));
    }
}
