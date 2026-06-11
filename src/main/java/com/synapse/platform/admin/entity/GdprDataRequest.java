package com.synapse.platform.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "gdpr_data_requests")
public class GdprDataRequest {

    private static final int DAYS_TO_PROCESS = 30;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_email", nullable = false)
    private String userEmail;

    @Column(name = "user_display_name")
    private String userDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 40)
    private GdprDataRequestType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private GdprDataRequestStatus status = GdprDataRequestStatus.PENDING;

    @Column(length = 500)
    private String reason;

    @Column(name = "admin_note", length = 1000)
    private String adminNote;

    @Column(name = "data_summary", columnDefinition = "TEXT")
    private String dataSummary;

    @Column(name = "execution_log", columnDefinition = "TEXT")
    private String executionLog;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected GdprDataRequest() {
    }

    public static GdprDataRequest create(
            UUID userId,
            String userEmail,
            String userDisplayName,
            GdprDataRequestType type,
            String reason,
            OffsetDateTime now) {
        GdprDataRequest request = new GdprDataRequest();
        request.userId = userId;
        request.userEmail = userEmail;
        request.userDisplayName = userDisplayName;
        request.type = type;
        request.status = GdprDataRequestStatus.PENDING;
        request.reason = blankToNull(reason);
        request.receivedAt = now;
        request.dueAt = now.plusDays(DAYS_TO_PROCESS);
        request.dataSummary = "Platform local request accepted for " + userEmail;
        request.executionLog = logLine(now, "Request created");
        return request;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (receivedAt == null) {
            receivedAt = now;
        }
        if (dueAt == null) {
            dueAt = receivedAt.plusDays(DAYS_TO_PROCESS);
        }
        if (status == null) {
            status = GdprDataRequestStatus.PENDING;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void approve(String note, OffsetDateTime now) {
        requireStatus(GdprDataRequestStatus.PENDING, "Only pending requests can be approved");
        status = GdprDataRequestStatus.PROCESSING;
        adminNote = blankToNull(note);
        appendLog(now, "Request approved");
        updatedAt = now;
    }

    public void execute(String note, OffsetDateTime now) {
        requireStatus(GdprDataRequestStatus.PROCESSING, "Only processing requests can be executed");
        if (type == GdprDataRequestType.DATA_ERASURE) {
            throw new IllegalStateException("Data erasure requests require dedicated deletion workflow");
        }
        status = GdprDataRequestStatus.COMPLETED;
        processedAt = now;
        adminNote = blankToNull(note);
        dataSummary = "Platform local request completed for " + userEmail;
        appendLog(now, "Request executed");
        updatedAt = now;
    }

    public void reject(String note, OffsetDateTime now) {
        if (status != GdprDataRequestStatus.PENDING && status != GdprDataRequestStatus.PROCESSING) {
            throw new IllegalStateException("Only pending or processing requests can be rejected");
        }
        status = GdprDataRequestStatus.REJECTED;
        processedAt = now;
        adminNote = blankToNull(note);
        appendLog(now, "Request rejected");
        updatedAt = now;
    }

    public boolean isOpen() {
        return status == GdprDataRequestStatus.PENDING || status == GdprDataRequestStatus.PROCESSING;
    }

    private void requireStatus(GdprDataRequestStatus expected, String message) {
        if (status != expected) {
            throw new IllegalStateException(message);
        }
    }

    private void appendLog(OffsetDateTime at, String message) {
        String line = logLine(at, message);
        if (executionLog == null || executionLog.isBlank()) {
            executionLog = line;
            return;
        }
        executionLog = executionLog + "\n" + line;
    }

    private static String logLine(OffsetDateTime at, String message) {
        return at.toString() + " " + message;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserDisplayName() {
        return userDisplayName;
    }

    public GdprDataRequestType getType() {
        return type;
    }

    public GdprDataRequestStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public String getAdminNote() {
        return adminNote;
    }

    public String getDataSummary() {
        return dataSummary;
    }

    public String getExecutionLog() {
        return executionLog;
    }

    public OffsetDateTime getReceivedAt() {
        return receivedAt;
    }

    public OffsetDateTime getDueAt() {
        return dueAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
