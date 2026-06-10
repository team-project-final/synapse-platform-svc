package com.synapse.platform.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse;
import com.synapse.platform.audit.api.AuditAnalyticsApi;
import com.synapse.platform.audit.api.AuditAnalyticsSnapshot;
import com.synapse.platform.audit.api.RecentAuditActivity;
import com.synapse.platform.auth.api.TenantAnalyticsApi;
import com.synapse.platform.auth.api.TenantAnalyticsSnapshot;
import com.synapse.platform.billing.api.BillingAnalyticsApi;
import com.synapse.platform.billing.api.BillingAnalyticsSnapshot;
import com.synapse.platform.notification.api.NotificationAnalyticsApi;
import com.synapse.platform.notification.api.NotificationAnalyticsSnapshot;
import com.synapse.platform.user.api.UserAnalyticsApi;
import com.synapse.platform.user.api.UserAnalyticsSnapshot;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAnalyticsServiceTest {

    @Mock
    private UserAnalyticsApi userAnalyticsApi;

    @Mock
    private TenantAnalyticsApi tenantAnalyticsApi;

    @Mock
    private BillingAnalyticsApi billingAnalyticsApi;

    @Mock
    private NotificationAnalyticsApi notificationAnalyticsApi;

    @Mock
    private AuditAnalyticsApi auditAnalyticsApi;

    @Test
    void getSummary_shouldCombinePlatformLocalMetricsAndDisconnectedCrossServiceItems() {
        UUID activityId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OffsetDateTime activityAt = OffsetDateTime.parse("2026-06-10T10:00:00+09:00");
        given(userAnalyticsApi.getUserAnalytics(any(OffsetDateTime.class)))
                .willReturn(new UserAnalyticsSnapshot(10, 8, 1, 1, 2, 3, 7, "USERS_LAST_LOGIN_AT"));
        given(tenantAnalyticsApi.getTenantAnalytics())
                .willReturn(new TenantAnalyticsSnapshot(4, 3, 1, Map.of("free", 3L, "pro", 1L)));
        given(billingAnalyticsApi.getBillingAnalytics(any(OffsetDateTime.class)))
                .willReturn(new BillingAnalyticsSnapshot(2, 1, 9900));
        given(notificationAnalyticsApi.getNotificationAnalytics(any(OffsetDateTime.class)))
                .willReturn(new NotificationAnalyticsSnapshot(12, 1));
        given(auditAnalyticsApi.getAuditAnalytics(any(OffsetDateTime.class), anyInt()))
                .willReturn(new AuditAnalyticsSnapshot(5, List.of(new RecentAuditActivity(
                        activityId,
                        "USER_LOGIN",
                        userId,
                        "USER",
                        userId.toString(),
                        activityAt))));

        AdminAnalyticsSummaryResponse result = service().getSummary();

        assertThat(result.users().total()).isEqualTo(10);
        assertThat(result.users().dau()).isEqualTo(3);
        assertThat(result.tenants().plans()).containsEntry("pro", 1L);
        assertThat(result.usage())
                .anySatisfy(item -> {
                    assertThat(item.key()).isEqualTo("notifications.sent.today");
                    assertThat(item.value()).isEqualTo(12);
                    assertThat(item.status()).isEqualTo("OK");
                })
                .anySatisfy(item -> {
                    assertThat(item.key()).isEqualTo("ai.tokens.monthly");
                    assertThat(item.value()).isNull();
                    assertThat(item.status()).isEqualTo("NOT_CONNECTED");
                });
        assertThat(result.pendingItems())
                .anySatisfy(item -> {
                    assertThat(item.key()).isEqualTo("data-requests");
                    assertThat(item.status()).isEqualTo("NOT_IMPLEMENTED");
                });
        assertThat(result.recentActivities())
                .singleElement()
                .satisfies(activity -> {
                    assertThat(activity.id()).isEqualTo(activityId);
                    assertThat(activity.action()).isEqualTo("USER_LOGIN");
                    assertThat(activity.userId()).isEqualTo(userId);
                });
    }

    private AdminAnalyticsService service() {
        return new AdminAnalyticsService(
                userAnalyticsApi,
                tenantAnalyticsApi,
                billingAnalyticsApi,
                notificationAnalyticsApi,
                auditAnalyticsApi);
    }
}
