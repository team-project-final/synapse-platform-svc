package com.synapse.platform.audit.consumer;

import com.synapse.platform.audit.service.AuditLogService;
import org.apache.avro.generic.GenericRecord;
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
            groupId = "audit-consumer-group",
            containerFactory = "auditKafkaListenerContainerFactory")
    public void consume(GenericRecord envelope) {
        auditLogService.processEvent(envelope);
    }
}
