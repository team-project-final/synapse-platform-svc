package com.synapse.platform.audit.service;

import com.synapse.platform.audit.api.AuditAnalyticsApi;
import com.synapse.platform.audit.api.AuditAnalyticsSnapshot;
import com.synapse.platform.audit.api.RecentAuditActivity;
import com.synapse.platform.audit.entity.AuditLog;
import com.synapse.platform.audit.repository.AuditLogRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditAnalyticsService implements AuditAnalyticsApi {

    private final AuditLogRepository auditLogRepository;

    public AuditAnalyticsService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AuditAnalyticsSnapshot getAuditAnalytics(OffsetDateTime now, int recentActivitySize) {
        OffsetDateTime dayStart = startOfDay(now);
        return new AuditAnalyticsSnapshot(
                auditLogRepository.countByCreatedAtGreaterThanEqual(dayStart),
                findRecentActivities(recentActivitySize));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RecentAuditActivity> findRecentActivities(int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size)).stream()
                .map(this::toActivity)
                .toList();
    }

    private RecentAuditActivity toActivity(AuditLog auditLog) {
        return new RecentAuditActivity(
                auditLog.getId(),
                auditLog.getAction(),
                auditLog.getUserId(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                auditLog.getCreatedAt());
    }

    private OffsetDateTime startOfDay(OffsetDateTime now) {
        return now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
    }
}
