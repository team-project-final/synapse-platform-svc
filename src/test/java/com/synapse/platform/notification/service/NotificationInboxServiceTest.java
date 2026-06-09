package com.synapse.platform.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.synapse.platform.notification.dto.response.NotificationItemResponse;
import com.synapse.platform.notification.dto.response.NotificationPageResponse;
import com.synapse.platform.notification.dto.response.NotificationReadAllResponse;
import com.synapse.platform.notification.dto.response.NotificationUnreadCountResponse;
import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.entity.NotificationStatus;
import com.synapse.platform.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationInboxServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    void list_shouldReturnInboxNotifications() {
        UUID userId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Notification notification = sentNotification(UUID.randomUUID(), userId);
        NotificationInboxService service = service();
        given(notificationRepository.findByUserIdAndChannelAndStatus(
                userId,
                NotificationChannel.FCM,
                NotificationStatus.SENT,
                pageable)).willReturn(new PageImpl<>(List.of(notification), pageable, 1));

        NotificationPageResponse response = service.list(userId, pageable);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().id()).isEqualTo(notification.getId());
        assertThat(response.items().getFirst().read()).isFalse();
    }

    @Test
    void countUnread_shouldReturnUnreadCount() {
        UUID userId = UUID.randomUUID();
        NotificationInboxService service = service();
        given(notificationRepository.countByUserIdAndChannelAndStatusAndReadAtIsNull(
                userId,
                NotificationChannel.FCM,
                NotificationStatus.SENT)).willReturn(4L);

        NotificationUnreadCountResponse response = service.countUnread(userId);

        assertThat(response.count()).isEqualTo(4L);
    }

    @Test
    void markRead_existingInboxNotification_shouldMarkRead() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        Notification notification = sentNotification(notificationId, userId);
        NotificationInboxService service = service();
        given(notificationRepository.findByIdAndUserIdAndChannelAndStatus(
                notificationId,
                userId,
                NotificationChannel.FCM,
                NotificationStatus.SENT)).willReturn(Optional.of(notification));

        NotificationItemResponse response = service.markRead(userId, notificationId);

        assertThat(response.read()).isTrue();
        assertThat(response.readAt()).isNotNull();
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markRead_missingOrForeignNotification_shouldThrowNotFound() {
        UUID userId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        NotificationInboxService service = service();
        given(notificationRepository.findByIdAndUserIdAndChannelAndStatus(
                notificationId,
                userId,
                NotificationChannel.FCM,
                NotificationStatus.SENT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(userId, notificationId))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessage("Notification not found");
    }

    @Test
    void markAllRead_shouldReturnUpdatedCount() {
        UUID userId = UUID.randomUUID();
        NotificationInboxService service = service();
        given(notificationRepository.markAllRead(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(NotificationChannel.FCM),
                org.mockito.ArgumentMatchers.eq(NotificationStatus.SENT),
                org.mockito.ArgumentMatchers.any(Instant.class))).willReturn(2);

        NotificationReadAllResponse response = service.markAllRead(userId);

        assertThat(response.updatedCount()).isEqualTo(2);
        verify(notificationRepository).markAllRead(
                org.mockito.ArgumentMatchers.eq(userId),
                org.mockito.ArgumentMatchers.eq(NotificationChannel.FCM),
                org.mockito.ArgumentMatchers.eq(NotificationStatus.SENT),
                org.mockito.ArgumentMatchers.any(Instant.class));
    }

    private NotificationInboxService service() {
        return new NotificationInboxService(notificationRepository);
    }

    private static Notification sentNotification(UUID notificationId, UUID userId) {
        Notification notification = Notification.create(
                UUID.randomUUID(),
                userId,
                UUID.randomUUID(),
                "CARD_REVIEW_DUE",
                NotificationChannel.FCM,
                "Review due",
                "A card is ready.");
        ReflectionTestUtils.setField(notification, "id", notificationId);
        ReflectionTestUtils.setField(notification, "createdAt", Instant.parse("2026-06-09T05:00:00Z"));
        notification.markSent();
        return notification;
    }
}
