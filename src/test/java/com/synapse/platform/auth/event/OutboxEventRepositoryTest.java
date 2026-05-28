package com.synapse.platform.auth.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutboxEventRepositoryTest {

    @Autowired
    private OutboxEventRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void resetTimedOutPublishing_shouldRestoreOnlyExpiredPublishingEvents() {
        OutboxEvent expired = repository.saveAndFlush(event());
        OutboxEvent active = repository.saveAndFlush(event());
        OffsetDateTime now = OffsetDateTime.now();

        assertThat(repository.claimPending(expired.getId(), now.minusSeconds(1))).isOne();
        assertThat(repository.claimPending(active.getId(), now.plusMinutes(5))).isOne();
        entityManager.clear();

        assertThat(repository.resetTimedOutPublishing(now)).isOne();
        entityManager.clear();

        assertThat(repository.findById(expired.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PENDING);
        assertThat(repository.findById(active.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxEventStatus.PUBLISHING);
    }

    private OutboxEvent event() {
        return OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                UUID.randomUUID().toString(),
                "com.synapse.event.platform.UserRegistered",
                PlatformAvroEvents.userRegisteredEnvelopeBytes(
                        UUID.randomUUID(),
                        "new@example.com",
                        "New User",
                        UUID.randomUUID()));
    }
}
