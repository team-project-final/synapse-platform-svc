package com.synapse.platform.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.synapse.platform.audit.api.AuditAnalyticsSnapshot;
import com.synapse.platform.audit.entity.AuditLog;
import com.synapse.platform.audit.repository.AuditLogRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuditAnalyticsServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void getAuditAnalytics_shouldCountTodayActivitiesAndReturnRecentActivities() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-10T12:00:00+09:00");
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-06-10T00:00:00+09:00");
        UUID logId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuditLog auditLog = AuditLog.of(UUID.randomUUID(), "USER_LOGIN", userId, "USER", userId.toString(), "{}");
        ReflectionTestUtils.setField(auditLog, "id", logId);
        ReflectionTestUtils.setField(auditLog, "createdAt", now.minusMinutes(10));
        given(auditLogRepository.countByCreatedAtGreaterThanEqual(dayStart)).willReturn(5L);
        given(auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 5)))
                .willReturn(new PageImpl<>(List.of(auditLog)));

        AuditAnalyticsSnapshot result = new AuditAnalyticsService(auditLogRepository).getAuditAnalytics(now, 5);

        assertThat(result.activitiesToday()).isEqualTo(5);
        assertThat(result.recentActivities())
                .singleElement()
                .satisfies(activity -> {
                    assertThat(activity.id()).isEqualTo(logId);
                    assertThat(activity.action()).isEqualTo("USER_LOGIN");
                    assertThat(activity.userId()).isEqualTo(userId);
                });
    }
}
