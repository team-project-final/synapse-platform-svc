package com.synapse.platform.audit.consumer;

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
}
