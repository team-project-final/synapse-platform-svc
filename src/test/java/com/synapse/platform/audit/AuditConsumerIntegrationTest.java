package com.synapse.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.synapse.engagement.BadgeEarned;
import com.synapse.engagement.LevelUp;
import com.synapse.knowledge.NoteCreated;
import com.synapse.knowledge.NoteUpdated;
import com.synapse.learning.Rating;
import com.synapse.learning.ReviewCompleted;
import com.synapse.platform.audit.repository.AuditLogRepository;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test for audit log consumption of the five new domain-event topics (S6).
 *
 * <p>Uses EmbeddedKafka + mock Schema Registry (mock://platform-test — configured in
 * src/test/resources/application.yml). Each test produces one event, awaits the
 * AuditKafkaConsumer to pick it up, and asserts the persisted AuditLog row.
 *
 * <p>Testcontainers Kafka+SR harness is NOT present in this project, so full end-to-end
 * wire-format validation (Confluent wire-prefix byte, schema evolution) is deferred to
 * local docker-compose / CI environment.
 */
@SpringBootTest(properties = "app.kafka.outbox.enabled=true")
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {
            "platform.auth.user-registered-v1",
            "knowledge.note.note-created-v1",
            "knowledge.note.note-updated-v1",
            "learning.card.review-completed-v1",
            "engagement.gamification.badge-earned-v1",
            "engagement.gamification.level-up-v1"
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditConsumerIntegrationTest {

    private static final String TOPIC_NOTE_CREATED = "knowledge.note.note-created-v1";
    private static final String TOPIC_NOTE_UPDATED = "knowledge.note.note-updated-v1";
    private static final String TOPIC_REVIEW = "learning.card.review-completed-v1";
    private static final String TOPIC_BADGE = "engagement.gamification.badge-earned-v1";
    private static final String TOPIC_LEVEL_UP = "engagement.gamification.level-up-v1";

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    @Qualifier("eventKafkaTemplate")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void cleanUp() {
        auditLogRepository.deleteAll();
    }

    // ── NoteCreated ───────────────────────────────────────────────────────────

    @Test
    @Order(1)
    void noteCreatedEvent_shouldBeStoredAsNoteCreatedAuditLog() {
        String eventId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        NoteCreated event = NoteCreated.newBuilder()
                .setEventId(eventId)
                .setNoteId(noteId)
                .setUserId(userId)
                .setTenantId(UUID.randomUUID().toString())
                .setTitle("Integration test note")
                .setCreatedAt("2026-06-04T00:00:00Z")
                .setOccurredAt(System.currentTimeMillis())
                .build();

        kafkaTemplate.send(TOPIC_NOTE_CREATED, userId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(auditLogRepository.findByAction("NOTE_CREATED", Pageable.unpaged())
                        .getContent())
                        .singleElement()
                        .satisfies(log -> {
                            assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
                            assertThat(log.getAction()).isEqualTo("NOTE_CREATED");
                            assertThat(log.getResourceType()).isEqualTo("NOTE");
                            assertThat(log.getResourceId()).isEqualTo(noteId);
                            assertThat(log.getUserId()).isEqualTo(UUID.fromString(userId));
                        }));
    }

    // ── NoteUpdated ───────────────────────────────────────────────────────────

    @Test
    @Order(2)
    void noteUpdatedEvent_shouldBeStoredAsNoteUpdatedAuditLog() {
        String eventId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        NoteUpdated event = NoteUpdated.newBuilder()
                .setEventId(eventId)
                .setNoteId(noteId)
                .setUserId(userId)
                .setTenantId(UUID.randomUUID().toString())
                .setTitle("Updated title")
                .setUpdatedAt("2026-06-04T01:00:00Z")
                .setOccurredAt(System.currentTimeMillis())
                .build();

        kafkaTemplate.send(TOPIC_NOTE_UPDATED, userId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(auditLogRepository.findByAction("NOTE_UPDATED", Pageable.unpaged())
                        .getContent())
                        .singleElement()
                        .satisfies(log -> {
                            assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
                            assertThat(log.getAction()).isEqualTo("NOTE_UPDATED");
                            assertThat(log.getResourceType()).isEqualTo("NOTE");
                            assertThat(log.getResourceId()).isEqualTo(noteId);
                            assertThat(log.getUserId()).isEqualTo(UUID.fromString(userId));
                        }));
    }

    // ── ReviewCompleted ───────────────────────────────────────────────────────

    @Test
    @Order(3)
    void reviewCompletedEvent_shouldBeStoredAsReviewCompletedAuditLog() {
        String eventId = UUID.randomUUID().toString();
        String cardId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        ReviewCompleted event = ReviewCompleted.newBuilder()
                .setEventId(eventId)
                .setCardId(cardId)
                .setUserId(userId)
                .setTenantId(UUID.randomUUID().toString())
                .setRating(Rating.GOOD)
                .setNextReviewAt("2026-06-05T00:00:00Z")
                .setReviewedAt("2026-06-04T00:00:00Z")
                .setOccurredAt(System.currentTimeMillis())
                .build();

        kafkaTemplate.send(TOPIC_REVIEW, userId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(auditLogRepository.findByAction("REVIEW_COMPLETED", Pageable.unpaged())
                        .getContent())
                        .singleElement()
                        .satisfies(log -> {
                            assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
                            assertThat(log.getAction()).isEqualTo("REVIEW_COMPLETED");
                            assertThat(log.getResourceType()).isEqualTo("CARD");
                            assertThat(log.getResourceId()).isEqualTo(cardId);
                            assertThat(log.getUserId()).isEqualTo(UUID.fromString(userId));
                        }));
    }

    // ── BadgeEarned — engagement userId is a Long string → user_id must be null ─

    @Test
    @Order(4)
    void badgeEarnedEvent_nonUuidUserId_shouldHaveNullUserIdInAuditLog() {
        String eventId = UUID.randomUUID().toString();
        String badgeId = UUID.randomUUID().toString();
        String userId = "12345";   // Long-stringified — NOT a UUID

        BadgeEarned event = BadgeEarned.newBuilder()
                .setEventId(eventId)
                .setTenantId(UUID.randomUUID().toString())
                .setUserId(userId)
                .setBadgeId(badgeId)
                .setBadgeCode("STREAK_7")
                .setOccurredAt(System.currentTimeMillis())
                .build();

        kafkaTemplate.send(TOPIC_BADGE, userId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(auditLogRepository.findByAction("BADGE_EARNED", Pageable.unpaged())
                        .getContent())
                        .singleElement()
                        .satisfies(log -> {
                            assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
                            assertThat(log.getAction()).isEqualTo("BADGE_EARNED");
                            assertThat(log.getResourceType()).isEqualTo("BADGE");
                            assertThat(log.getResourceId()).isEqualTo(badgeId);
                            assertThat(log.getUserId()).isNull();
                        }));
    }

    // ── LevelUp — userId is a Long string, resourceId = userId string ─────────

    @Test
    @Order(5)
    void levelUpEvent_nonUuidUserId_shouldHaveNullUserIdAndUserIdAsResourceId() {
        String eventId = UUID.randomUUID().toString();
        String userId = "99";   // Long-stringified — NOT a UUID

        LevelUp event = LevelUp.newBuilder()
                .setEventId(eventId)
                .setTenantId(UUID.randomUUID().toString())
                .setUserId(userId)
                .setNewLevel(5)
                .setPreviousLevel(4)
                .setTotalXp(1000L)
                .setOccurredAt(System.currentTimeMillis())
                .build();

        kafkaTemplate.send(TOPIC_LEVEL_UP, userId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(auditLogRepository.findByAction("LEVEL_UP", Pageable.unpaged())
                        .getContent())
                        .singleElement()
                        .satisfies(log -> {
                            assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
                            assertThat(log.getAction()).isEqualTo("LEVEL_UP");
                            assertThat(log.getResourceType()).isEqualTo("USER");
                            assertThat(log.getResourceId()).isEqualTo(userId);
                            assertThat(log.getUserId()).isNull();
                        }));
    }

    // ── Idempotency: duplicate event_id → only one audit log row ─────────────

    @Test
    @Order(6)
    void duplicateNoteCreatedEvent_shouldStoreOnlyOneAuditLog() {
        String eventId = UUID.randomUUID().toString();
        String noteId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        NoteCreated event = NoteCreated.newBuilder()
                .setEventId(eventId)
                .setNoteId(noteId)
                .setUserId(userId)
                .setTenantId(UUID.randomUUID().toString())
                .setTitle("Duplicate test")
                .setCreatedAt("2026-06-04T00:00:00Z")
                .setOccurredAt(System.currentTimeMillis())
                .build();

        kafkaTemplate.send(TOPIC_NOTE_CREATED, userId, event);
        kafkaTemplate.send(TOPIC_NOTE_CREATED, userId, event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(auditLogRepository.findAll()).hasSize(1));
    }
}
