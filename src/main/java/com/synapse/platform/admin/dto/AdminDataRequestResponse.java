package com.synapse.platform.admin.dto;

import com.synapse.platform.admin.entity.GdprDataRequest;
import com.synapse.platform.admin.entity.GdprDataRequestStatus;
import com.synapse.platform.admin.entity.GdprDataRequestType;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public record AdminDataRequestResponse(
        UUID id,
        UUID userId,
        String userEmail,
        String userDisplayName,
        GdprDataRequestType type,
        String typeLabel,
        GdprDataRequestStatus status,
        String statusLabel,
        OffsetDateTime receivedAt,
        OffsetDateTime dueAt,
        int daysRemaining,
        OffsetDateTime processedAt,
        String reason,
        String adminNote,
        String dataSummary,
        String latestLog,
        List<String> executionLogs
) {

    private static final long MILLIS_PER_DAY = Duration.ofDays(1).toMillis();

    public AdminDataRequestResponse {
        executionLogs = List.copyOf(executionLogs);
    }

    @Override
    public List<String> executionLogs() {
        return List.copyOf(executionLogs);
    }

    public static AdminDataRequestResponse from(GdprDataRequest request) {
        List<String> logs = logs(request.getExecutionLog());
        return new AdminDataRequestResponse(
                request.getId(),
                request.getUserId(),
                request.getUserEmail(),
                request.getUserDisplayName(),
                request.getType(),
                typeLabel(request.getType()),
                request.getStatus(),
                statusLabel(request.getStatus()),
                request.getReceivedAt(),
                request.getDueAt(),
                daysRemaining(request),
                request.getProcessedAt(),
                request.getReason(),
                request.getAdminNote(),
                request.getDataSummary(),
                logs.isEmpty() ? null : logs.get(logs.size() - 1),
                logs);
    }

    private static int daysRemaining(GdprDataRequest request) {
        if (!request.isOpen() || request.getDueAt() == null) {
            return 0;
        }
        long millisRemaining = Duration.between(OffsetDateTime.now(ZoneOffset.UTC), request.getDueAt()).toMillis();
        if (millisRemaining <= 0) {
            return 0;
        }
        long days = (millisRemaining + MILLIS_PER_DAY - 1) / MILLIS_PER_DAY;
        return Math.toIntExact(days);
    }

    private static List<String> logs(String executionLog) {
        if (executionLog == null || executionLog.isBlank()) {
            return List.of();
        }
        return Arrays.stream(executionLog.split("\\R"))
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static String typeLabel(GdprDataRequestType type) {
        return switch (type) {
            case DATA_ACCESS -> "데이터 열람";
            case DATA_EXPORT -> "데이터 내보내기";
            case DATA_ERASURE -> "데이터 삭제";
        };
    }

    private static String statusLabel(GdprDataRequestStatus status) {
        return switch (status) {
            case PENDING -> "대기";
            case PROCESSING -> "처리중";
            case COMPLETED -> "완료";
            case REJECTED -> "거부";
        };
    }
}
