package com.synapse.platform.audit.consumer;

import com.synapse.engagement.BadgeEarned;
import com.synapse.engagement.LevelUp;
import com.synapse.knowledge.NoteCreated;
import com.synapse.knowledge.NoteUpdated;
import com.synapse.learning.ReviewCompleted;
import com.synapse.platform.UserRegistered;
import com.synapse.platform.audit.service.AuditLogService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AuditKafkaConsumer {

    private final AuditLogService auditLogService;

    public AuditKafkaConsumer(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @KafkaListener(
            topics = {"${app.kafka.topics.user-registered:platform.auth.user-registered-v1}"},
            groupId = "platform-svc-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consume(UserRegistered event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.note-created}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeNoteCreated(NoteCreated event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.note-updated}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeNoteUpdated(NoteUpdated event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.review-completed}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeReviewCompleted(ReviewCompleted event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.badge-earned}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeBadgeEarned(BadgeEarned event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "${app.kafka.topics.level-up}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeLevelUp(LevelUp event) {
        auditLogService.processEvent(event);
    }
}
