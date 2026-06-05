package com.synapse.platform.auth.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
// Kafka 게이트(synapse.kafka.enabled=true)일 때만 eventKafkaTemplate가 존재하므로 그 조건을 함께 요구한다.
// app.kafka.outbox.enabled(기본 true)는 outbox drain on/off 별개 플래그(#59).
@ConditionalOnExpression("${synapse.kafka.enabled:false} and ${app.kafka.outbox.enabled:true}")
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.outbox.publish-lease-ms:60000}")
    private long publishLeaseMs = 60000L;

    public OutboxEventPublisher(
            OutboxEventRepository repository,
            @Qualifier("eventKafkaTemplate") KafkaTemplate<String, Object> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${app.kafka.outbox.publish-delay-ms:1000}")
    @Transactional
    public void publishPendingEvents() {
        OffsetDateTime now = OffsetDateTime.now();
        repository.resetTimedOutPublishing(now);
        List<OutboxEvent> events = repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                OutboxEventStatus.PENDING,
                now);
        events.stream()
                .filter(this::claim)
                .forEach(this::publish);
    }

    private boolean claim(OutboxEvent event) {
        OffsetDateTime leaseExpiresAt = OffsetDateTime.now().plus(Duration.ofMillis(publishLeaseMs));
        if (repository.claimPending(event.getId(), leaseExpiresAt) != 1) {
            return false;
        }
        event.markPublishing(leaseExpiresAt);
        return true;
    }

    private void publish(OutboxEvent event) {
        try {
            UserRegisteredOutboxPayload payload = objectMapper.readValue(
                    event.getPayload(), UserRegisteredOutboxPayload.class);
            kafkaTemplate.send(event.getTopic(), payload.tenantId(), payload.toRecord())
                    .whenComplete((result, exception) -> {
                        if (exception == null) {
                            event.markPublished();
                            repository.save(event);
                            return;
                        }
                        event.markPublishFailed(exception);
                        repository.save(event);
                        logPublishFailure(event, exception);
                    });
        } catch (IOException | RuntimeException exception) {
            event.markPublishFailed(exception);
            repository.save(event);
            logPublishFailure(event, exception);
        }
    }

    private void logPublishFailure(OutboxEvent event, Throwable exception) {
        log.error(
                "Failed to publish outbox event: id={}, type={}",
                event.getId(),
                event.getEventType(),
                exception);
    }
}
