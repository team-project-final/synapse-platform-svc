package com.synapse.platform.audit.consumer;

import com.synapse.engagement.BadgeEarned;
import com.synapse.engagement.LevelUp;
import com.synapse.knowledge.NoteCreated;
import com.synapse.knowledge.NoteUpdated;
import com.synapse.learning.ReviewCompleted;
import com.synapse.platform.NotificationSend;
import com.synapse.platform.UserRegistered;
import com.synapse.platform.audit.service.AuditLogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "synapse.kafka", name = "enabled", havingValue = "true")
public class AuditKafkaConsumer {

    private final AuditLogService auditLogService;

    public AuditKafkaConsumer(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @KafkaListener(
            topics = "#{@kafkaTopicResolver.userRegistered()}",
            groupId = "platform-svc-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consume(UserRegistered event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicResolver.noteCreated()}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeNoteCreated(NoteCreated event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicResolver.noteUpdated()}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeNoteUpdated(NoteUpdated event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicResolver.reviewCompleted()}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeReviewCompleted(ReviewCompleted event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicResolver.badgeEarned()}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeBadgeEarned(BadgeEarned event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicResolver.levelUp()}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeLevelUp(LevelUp event) {
        auditLogService.processEvent(event);
    }

    @KafkaListener(
            topics = "#{@kafkaTopicResolver.notificationSend()}",
            groupId = "platform-audit-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consumeNotificationSend(NotificationSend event) {
        auditLogService.processEvent(event);
    }
}
