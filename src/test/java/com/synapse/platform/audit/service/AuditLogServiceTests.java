package com.synapse.platform.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.synapse.engagement.BadgeEarned;
import com.synapse.engagement.LevelUp;
import com.synapse.knowledge.NoteCreated;
import com.synapse.knowledge.NoteUpdated;
import com.synapse.learning.ReviewCompleted;
import com.synapse.learning.Rating;
import com.synapse.platform.audit.entity.AuditLog;
import com.synapse.platform.audit.repository.AuditLogRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTests {

    @Mock
    private AuditLogRepository repository;

    private AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(repository);
    }

    // ── NoteCreated ───────────────────────────────────────────────────────────

    @Test
    void mapsNoteCreated() {
        String eventId = UUID.randomUUID().toString();
        String noteId  = UUID.randomUUID().toString();
        String userId  = UUID.randomUUID().toString();

        NoteCreated event = NoteCreated.newBuilder()
                .setEventId(eventId)
                .setNoteId(noteId)
                .setUserId(userId)
                .setTenantId("tenant-1")
                .setTitle("My note")
                .setCreatedAt("2026-06-04T00:00:00Z")
                .setOccurredAt(0L)
                .build();

        service.processEvent(event);

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();

        assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
        assertThat(log.getAction()).isEqualTo("NOTE_CREATED");
        assertThat(log.getResourceType()).isEqualTo("NOTE");
        assertThat(log.getResourceId()).isEqualTo(noteId);
        assertThat(log.getUserId()).isEqualTo(UUID.fromString(userId));
    }

    // ── NoteUpdated ───────────────────────────────────────────────────────────

    @Test
    void mapsNoteUpdated() {
        String eventId = UUID.randomUUID().toString();
        String noteId  = UUID.randomUUID().toString();
        String userId  = UUID.randomUUID().toString();

        NoteUpdated event = NoteUpdated.newBuilder()
                .setEventId(eventId)
                .setNoteId(noteId)
                .setUserId(userId)
                .setTenantId("tenant-1")
                .setTitle("My note updated")
                .setUpdatedAt("2026-06-04T01:00:00Z")
                .setOccurredAt(0L)
                .build();

        service.processEvent(event);

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();

        assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
        assertThat(log.getAction()).isEqualTo("NOTE_UPDATED");
        assertThat(log.getResourceType()).isEqualTo("NOTE");
        assertThat(log.getResourceId()).isEqualTo(noteId);
        assertThat(log.getUserId()).isEqualTo(UUID.fromString(userId));
    }

    // ── ReviewCompleted ───────────────────────────────────────────────────────

    @Test
    void mapsReviewCompleted() {
        String eventId = UUID.randomUUID().toString();
        String cardId  = UUID.randomUUID().toString();
        String userId  = UUID.randomUUID().toString();

        ReviewCompleted event = ReviewCompleted.newBuilder()
                .setEventId(eventId)
                .setCardId(cardId)
                .setUserId(userId)
                .setTenantId("tenant-1")
                .setRating(Rating.GOOD)
                .setNextReviewAt("2026-06-05T00:00:00Z")
                .setReviewedAt("2026-06-04T00:00:00Z")
                .setOccurredAt(0L)
                .build();

        service.processEvent(event);

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();

        assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
        assertThat(log.getAction()).isEqualTo("REVIEW_COMPLETED");
        assertThat(log.getResourceType()).isEqualTo("CARD");
        assertThat(log.getResourceId()).isEqualTo(cardId);
        assertThat(log.getUserId()).isEqualTo(UUID.fromString(userId));
    }

    // ── BadgeEarned — userId is a Long string, user_id must be null ───────────

    @Test
    void badgeEarnedHasNullUserIdWhenNonUuid() {
        String eventId  = UUID.randomUUID().toString();
        String badgeId  = UUID.randomUUID().toString();
        String userId   = "42";   // Long string — NOT a UUID

        BadgeEarned event = BadgeEarned.newBuilder()
                .setEventId(eventId)
                .setTenantId("tenant-1")
                .setUserId(userId)
                .setBadgeId(badgeId)
                .setBadgeCode("STREAK_7")
                .setOccurredAt(0L)
                .build();

        service.processEvent(event);

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();

        assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
        assertThat(log.getAction()).isEqualTo("BADGE_EARNED");
        assertThat(log.getResourceType()).isEqualTo("BADGE");
        assertThat(log.getResourceId()).isEqualTo(badgeId);
        assertThat(log.getUserId()).isNull();
    }

    // ── LevelUp — userId is a Long string, resourceId = userId string ─────────

    @Test
    void levelUpHasNullUserIdAndUserIdAsResourceId() {
        String eventId = UUID.randomUUID().toString();
        String userId  = "99";   // Long string — NOT a UUID

        LevelUp event = LevelUp.newBuilder()
                .setEventId(eventId)
                .setTenantId("tenant-1")
                .setUserId(userId)
                .setNewLevel(5)
                .setPreviousLevel(4)
                .setTotalXp(1000L)
                .setOccurredAt(0L)
                .build();

        service.processEvent(event);

        var captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        AuditLog log = captor.getValue();

        assertThat(log.getEventId()).isEqualTo(UUID.fromString(eventId));
        assertThat(log.getAction()).isEqualTo("LEVEL_UP");
        assertThat(log.getResourceType()).isEqualTo("USER");
        assertThat(log.getResourceId()).isEqualTo(userId);
        assertThat(log.getUserId()).isNull();
    }

    // ── Idempotency (DataIntegrityViolation is swallowed) — implicit via save helper ──
    // DataIntegrityViolationException swallow test intentionally deferred
    // (Mockito stub of RuntimeException throw on duplicate eventId)

}
