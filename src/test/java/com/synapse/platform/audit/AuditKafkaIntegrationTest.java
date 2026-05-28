package com.synapse.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.synapse.platform.audit.repository.AuditLogRepository;
import com.synapse.platform.auth.event.OutboxEventPublisher;
import com.synapse.platform.auth.event.OutboxEventRepository;
import com.synapse.platform.auth.event.OutboxEventStatus;
import com.synapse.platform.auth.event.UserEventPublisher;
import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import java.time.Duration;
import java.util.UUID;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "app.kafka.outbox.enabled=true")
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"platform.auth.user-registered-v1"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditKafkaIntegrationTest {

    private static final String TOPIC = "platform.auth.user-registered-v1";

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private UserEventPublisher userEventPublisher;

    @Autowired
    private OutboxEventPublisher outboxEventPublisher;

    @Autowired
    @Qualifier("eventKafkaTemplate")
    private KafkaTemplate<String, GenericRecord> kafkaTemplate;

    @BeforeEach
    void cleanUp() {
        auditLogRepository.deleteAll();
        outboxEventRepository.deleteAll();
    }

    @Test
    @Order(1)
    void userRegisteredEvent_shouldBeStoredInAuditLogs() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        GenericRecord event = PlatformAvroEvents.userRegisteredEnvelope(
                userId,
                "new@example.com",
                "New User",
                tenantId);

        kafkaTemplate.send(TOPIC, userId.toString(), event);

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(auditLogRepository.findAll())
                        .singleElement()
                        .satisfies(log -> {
                            assertThat(log.getEventId()).isEqualTo(UUID.fromString(event.get("id").toString()));
                            assertThat(log.getAction()).isEqualTo("USER_REGISTERED");
                            assertThat(log.getUserId()).isEqualTo(userId);
                            assertThat(log.getResourceType()).isEqualTo("USER");
                        }));
    }

    @Test
    @Order(2)
    void duplicateUserRegisteredEvent_shouldStoreOnlyOneAuditLog() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        GenericRecord event = PlatformAvroEvents.userRegisteredEnvelope(
                userId,
                "new@example.com",
                "New User",
                tenantId);

        kafkaTemplate.send(TOPIC, userId.toString(), event);
        kafkaTemplate.send(TOPIC, userId.toString(), event);

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                assertThat(auditLogRepository.findAll()).hasSize(1));
    }

    @Test
    @Order(3)
    void userRegisteredOutboxEvent_shouldBePublishedAndStoredInAuditLogs() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        userEventPublisher.publishUserRegistered(userId, "outbox@example.com", "Outbox User", tenantId);
        outboxEventPublisher.publishPendingEvents();

        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            assertThat(auditLogRepository.findAll())
                    .singleElement()
                    .satisfies(log -> {
                        assertThat(log.getAction()).isEqualTo("USER_REGISTERED");
                        assertThat(log.getUserId()).isEqualTo(userId);
                    });
            assertThat(outboxEventRepository.findAll())
                    .singleElement()
                    .satisfies(event ->
                            assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED));
        });
    }
}
