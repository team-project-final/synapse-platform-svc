package com.synapse.platform.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.entity.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository repository;

    @Test
    void findByEventIdAndChannel_existingNotification_shouldReturnIt() {
        UUID eventId = UUID.randomUUID();
        Notification notification = repository.save(notification(eventId, NotificationChannel.FCM));

        assertThat(repository.findByEventIdAndChannel(eventId, NotificationChannel.FCM))
                .contains(notification);
        assertThat(repository.findByEventIdAndChannel(eventId, NotificationChannel.EMAIL))
                .isEmpty();
    }

    @Test
    void save_duplicateEventIdAndChannel_shouldFail() {
        UUID eventId = UUID.randomUUID();
        repository.saveAndFlush(notification(eventId, NotificationChannel.EMAIL));

        assertThatThrownBy(() -> repository.saveAndFlush(notification(eventId, NotificationChannel.EMAIL)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void countTodayEmailByUserId_shouldCountOnlySentEmailAfterDayStart() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Instant dayStart = Instant.now().minusSeconds(60);

        Notification sentEmail = notification(UUID.randomUUID(), userId, NotificationChannel.EMAIL);
        sentEmail.markSent();
        repository.save(sentEmail);

        Notification pendingEmail = notification(UUID.randomUUID(), userId, NotificationChannel.EMAIL);
        repository.save(pendingEmail);

        Notification sentFcm = notification(UUID.randomUUID(), userId, NotificationChannel.FCM);
        sentFcm.markSent();
        repository.save(sentFcm);

        Notification otherUserEmail = notification(UUID.randomUUID(), otherUserId, NotificationChannel.EMAIL);
        otherUserEmail.markSent();
        repository.save(otherUserEmail);

        assertThat(repository.countTodayEmailByUserId(userId, dayStart)).isOne();
    }

    @Test
    void markFailed_shouldRecordFailedStatusAndErrorMessage() {
        Notification notification = notification(UUID.randomUUID(), NotificationChannel.FCM);

        notification.incrementAttempts();
        notification.markFailed("provider unavailable");

        assertThat(notification.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(notification.getAttempts()).isOne();
        assertThat(notification.getErrorMessage()).isEqualTo("provider unavailable");
    }

    private static Notification notification(UUID eventId, NotificationChannel channel) {
        return notification(eventId, UUID.randomUUID(), channel);
    }

    private static Notification notification(UUID eventId, UUID userId, NotificationChannel channel) {
        return Notification.create(
                eventId,
                userId,
                UUID.randomUUID(),
                "CARD_REVIEW_DUE",
                channel,
                "Review due",
                "A card is ready for review.");
    }
}
