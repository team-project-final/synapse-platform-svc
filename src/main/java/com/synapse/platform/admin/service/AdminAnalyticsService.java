package com.synapse.platform.admin.service;

import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.PendingItem;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.RecentActivity;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.TenantsSummary;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.UsageItem;
import com.synapse.platform.admin.dto.AdminAnalyticsSummaryResponse.UsersSummary;
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
import org.springframework.stereotype.Service;

@Service
public class AdminAnalyticsService {

    private static final int RECENT_ACTIVITY_SIZE = 5;
    private static final String OK = "OK";
    private static final String INFO = "INFO";
    private static final String NOT_CONNECTED = "NOT_CONNECTED";
    private static final String ACTION_REQUIRED = "ACTION_REQUIRED";

    private final UserAnalyticsApi userAnalyticsApi;
    private final TenantAnalyticsApi tenantAnalyticsApi;
    private final BillingAnalyticsApi billingAnalyticsApi;
    private final NotificationAnalyticsApi notificationAnalyticsApi;
    private final AuditAnalyticsApi auditAnalyticsApi;
    private final AdminDataRequestService adminDataRequestService;

    public AdminAnalyticsService(
            UserAnalyticsApi userAnalyticsApi,
            TenantAnalyticsApi tenantAnalyticsApi,
            BillingAnalyticsApi billingAnalyticsApi,
            NotificationAnalyticsApi notificationAnalyticsApi,
            AuditAnalyticsApi auditAnalyticsApi,
            AdminDataRequestService adminDataRequestService) {
        this.userAnalyticsApi = userAnalyticsApi;
        this.tenantAnalyticsApi = tenantAnalyticsApi;
        this.billingAnalyticsApi = billingAnalyticsApi;
        this.notificationAnalyticsApi = notificationAnalyticsApi;
        this.auditAnalyticsApi = auditAnalyticsApi;
        this.adminDataRequestService = adminDataRequestService;
    }

    public AdminAnalyticsSummaryResponse getSummary() {
        OffsetDateTime now = OffsetDateTime.now();
        UserAnalyticsSnapshot users = userAnalyticsApi.getUserAnalytics(now);
        TenantAnalyticsSnapshot tenants = tenantAnalyticsApi.getTenantAnalytics();
        BillingAnalyticsSnapshot billing = billingAnalyticsApi.getBillingAnalytics(now);
        NotificationAnalyticsSnapshot notifications = notificationAnalyticsApi.getNotificationAnalytics(now);
        AuditAnalyticsSnapshot audit = auditAnalyticsApi.getAuditAnalytics(now, RECENT_ACTIVITY_SIZE);

        return new AdminAnalyticsSummaryResponse(
                now,
                toUsersSummary(users),
                toTenantsSummary(tenants),
                usageItems(billing, notifications, audit),
                pendingItems(adminDataRequestService.countOpenRequests()),
                audit.recentActivities().stream()
                        .map(this::toRecentActivity)
                        .toList());
    }

    private UsersSummary toUsersSummary(UserAnalyticsSnapshot users) {
        return new UsersSummary(
                users.total(),
                users.active(),
                users.suspended(),
                users.deleted(),
                users.newToday(),
                users.dau(),
                users.mau(),
                users.activitySource());
    }

    private TenantsSummary toTenantsSummary(TenantAnalyticsSnapshot tenants) {
        return new TenantsSummary(
                tenants.total(),
                tenants.active(),
                tenants.suspended(),
                tenants.plans());
    }

    private List<UsageItem> usageItems(
            BillingAnalyticsSnapshot billing,
            NotificationAnalyticsSnapshot notifications,
            AuditAnalyticsSnapshot audit) {
        return List.of(
                new UsageItem(
                        "notifications.sent.today",
                        "오늘 발송 알림",
                        notifications.sentToday(),
                        "count",
                        OK,
                        "notifications"),
                new UsageItem(
                        "notifications.failed.today",
                        "오늘 실패 알림",
                        notifications.failedToday(),
                        "count",
                        OK,
                        "notifications"),
                new UsageItem(
                        "subscriptions.active",
                        "활성 구독",
                        billing.activeSubscriptions(),
                        "count",
                        OK,
                        "billing"),
                new UsageItem(
                        "payments.succeeded.today",
                        "오늘 결제 성공",
                        billing.paidPaymentsToday(),
                        "count",
                        OK,
                        "billing"),
                new UsageItem(
                        "payments.revenue.today",
                        "오늘 결제 금액",
                        billing.revenueTodayMinorUnits(),
                        "minor_unit",
                        OK,
                        "billing"),
                new UsageItem(
                        "audit.activities.today",
                        "오늘 감사 이벤트",
                        audit.activitiesToday(),
                        "count",
                        OK,
                        "audit"),
                new UsageItem(
                        "ai.tokens.monthly",
                        "AI 토큰",
                        null,
                        "tokens",
                        NOT_CONNECTED,
                        "learning-ai"),
                new UsageItem(
                        "storage.used",
                        "스토리지",
                        null,
                        "bytes",
                        NOT_CONNECTED,
                        "knowledge"));
    }

    private List<PendingItem> pendingItems(long openDataRequests) {
        return List.of(
                new PendingItem(
                        "data-requests",
                        "GDPR 요청",
                        openDataRequests,
                        INFO,
                        dataRequestStatus(openDataRequests)),
                new PendingItem("reports", "신고", null, INFO, NOT_CONNECTED));
    }

    private String dataRequestStatus(long openDataRequests) {
        return openDataRequests > 0 ? ACTION_REQUIRED : OK;
    }

    private RecentActivity toRecentActivity(RecentAuditActivity activity) {
        return new RecentActivity(
                activity.id(),
                activity.action(),
                activity.userId(),
                activity.resourceType(),
                activity.resourceId(),
                activity.createdAt());
    }
}
