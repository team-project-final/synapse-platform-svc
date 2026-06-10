package com.synapse.platform.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.entity.NotificationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
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

        Notification passwordResetCodeEmail = notification(
                UUID.randomUUID(),
                userId,
                NotificationChannel.EMAIL,
                "PASSWORD_RESET_CODE");
        passwordResetCodeEmail.markSent();
        repository.save(passwordResetCodeEmail);

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
    void findByUserIdAndChannelAndStatus_shouldReturnOnlySentFcmInboxRowsNewestFirst() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Notification older = sentNotification(
                UUID.randomUUID(),
                userId,
                NotificationChannel.FCM,
                Instant.parse("2026-06-09T01:00:00Z"));
        Notification newer = sentNotification(
                UUID.randomUUID(),
                userId,
                NotificationChannel.FCM,
                Instant.parse("2026-06-09T02:00:00Z"));
        Notification sentEmail = sentNotification(
                UUID.randomUUID(),
                userId,
                NotificationChannel.EMAIL,
                Instant.parse("2026-06-09T03:00:00Z"));
        Notification failedFcm = notification(UUID.randomUUID(), userId, NotificationChannel.FCM);
        failedFcm.markFailed("provider unavailable");
        Notification otherUserFcm = sentNotification(
                UUID.randomUUID(),
                otherUserId,
                NotificationChannel.FCM,
                Instant.parse("2026-06-09T04:00:00Z"));
        repository.saveAll(List.of(older, newer, sentEmail, failedFcm, otherUserFcm));

        Page<Notification> result = repository.findByUserIdAndChannelAndStatus(
                userId,
                NotificationChannel.FCM,
                NotificationStatus.SENT,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertThat(result.getContent()).containsExactly(newer, older);
    }

    @Test
    void countUnreadAndMarkAllRead_shouldAffectOnlyUnreadSentFcmRowsForUser() {
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Notification unreadOne = sentNotification(UUID.randomUUID(), userId, NotificationChannel.FCM, Instant.now());
        Notification unreadTwo = sentNotification(UUID.randomUUID(), userId, NotificationChannel.FCM, Instant.now());
        Notification alreadyRead = sentNotification(UUID.randomUUID(), userId, NotificationChannel.FCM, Instant.now());
        alreadyRead.markRead(Instant.parse("2026-06-09T03:00:00Z"));
        Notification sentEmail = sentNotification(UUID.randomUUID(), userId, NotificationChannel.EMAIL, Instant.now());
        Notification otherUser = sentNotification(
                UUID.randomUUID(),
                otherUserId,
                NotificationChannel.FCM,
                Instant.now());
        repository.saveAll(List.of(unreadOne, unreadTwo, alreadyRead, sentEmail, otherUser));
        repository.flush();

        long count = repository.countByUserIdAndChannelAndStatusAndReadAtIsNull(
                userId,
                NotificationChannel.FCM,
                NotificationStatus.SENT);
        int updated = repository.markAllRead(
                userId,
                NotificationChannel.FCM,
                NotificationStatus.SENT,
                Instant.parse("2026-06-09T04:00:00Z"));

        assertThat(count).isEqualTo(2);
        assertThat(updated).isEqualTo(2);
        assertThat(repository.countByUserIdAndChannelAndStatusAndReadAtIsNull(
                userId,
                NotificationChannel.FCM,
                NotificationStatus.SENT)).isZero();
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

    @Test
    void markRead_alreadyRead_shouldKeepFirstReadAt() {
        Notification notification = notification(UUID.randomUUID(), NotificationChannel.FCM);
        Instant firstReadAt = Instant.parse("2026-06-09T05:00:00Z");

        notification.markRead(firstReadAt);
        notification.markRead(Instant.parse("2026-06-09T06:00:00Z"));

        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
    }

    @Test
    void analyticsCounts_shouldUseStatusAndTodayWindow() {
        Instant dayStart = Instant.parse("2026-06-10T00:00:00Z");
        UUID userId = UUID.randomUUID();
        long sentBaseline = repository.countByStatusAndSentAtGreaterThanEqual(NotificationStatus.SENT, dayStart);
        long failedBaseline = repository.countByStatusAndCreatedAtGreaterThanEqual(NotificationStatus.FAILED, dayStart);

        Notification sentToday = sentNotification(
                UUID.randomUUID(),
                userId,
                NotificationChannel.EMAIL,
                dayStart.plusSeconds(60));
        Notification sentBefore = sentNotification(
                UUID.randomUUID(),
                userId,
                NotificationChannel.EMAIL,
                dayStart.minusSeconds(1));
        Notification failedToday = failedNotification(dayStart.plusSeconds(120));
        Notification failedBefore = failedNotification(dayStart.minusSeconds(1));
        repository.saveAll(List.of(sentToday, sentBefore, failedToday, failedBefore));

        assertThat(repository.countByStatusAndSentAtGreaterThanEqual(NotificationStatus.SENT, dayStart))
                .isEqualTo(sentBaseline + 1);
        assertThat(repository.countByStatusAndCreatedAtGreaterThanEqual(NotificationStatus.FAILED, dayStart))
                .isEqualTo(failedBaseline + 1);
    }

    private static Notification notification(UUID eventId, NotificationChannel channel) {
        return notification(eventId, UUID.randomUUID(), channel);
    }

    private static Notification notification(UUID eventId, UUID userId, NotificationChannel channel) {
        return notification(eventId, userId, channel, "CARD_REVIEW_DUE");
    }

    private static Notification notification(
            UUID eventId,
            UUID userId,
            NotificationChannel channel,
            String notificationType) {
        return Notification.create(
                eventId,
                userId,
                UUID.randomUUID(),
                notificationType,
                channel,
                "Review due",
                "A card is ready for review.");
    }

    private static Notification sentNotification(
            UUID eventId,
            UUID userId,
            NotificationChannel channel,
            Instant createdAt) {
        Notification notification = notification(eventId, userId, channel);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        notification.markSent();
        ReflectionTestUtils.setField(notification, "sentAt", createdAt);
        return notification;
    }

    private static Notification failedNotification(Instant createdAt) {
        Notification notification = notification(UUID.randomUUID(), UUID.randomUUID(), NotificationChannel.EMAIL);
        ReflectionTestUtils.setField(notification, "createdAt", createdAt);
        notification.markFailed("provider unavailable");
        return notification;
    }
}
