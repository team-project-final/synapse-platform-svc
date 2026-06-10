package com.synapse.platform.notification.repository;

import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.entity.NotificationStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    Page<Notification> findByUserIdAndChannelAndStatus(
            UUID userId,
            NotificationChannel channel,
            NotificationStatus status,
            Pageable pageable);

    Optional<Notification> findByIdAndUserIdAndChannelAndStatus(
            UUID id,
            UUID userId,
            NotificationChannel channel,
            NotificationStatus status);

    long countByUserIdAndChannelAndStatusAndReadAtIsNull(
            UUID userId,
            NotificationChannel channel,
            NotificationStatus status);

    long countByStatusAndSentAtGreaterThanEqual(NotificationStatus status, Instant sentAt);

    long countByStatusAndCreatedAtGreaterThanEqual(NotificationStatus status, Instant createdAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification notification
               set notification.readAt = :readAt
             where notification.userId = :userId
               and notification.channel = :channel
               and notification.status = :status
               and notification.readAt is null
            """)
    int markAllRead(
            @Param("userId") UUID userId,
            @Param("channel") NotificationChannel channel,
            @Param("status") NotificationStatus status,
            @Param("readAt") Instant readAt);

    @Query("""
            select count(notification)
              from Notification notification
             where notification.userId = :userId
               and notification.channel = com.synapse.platform.notification.entity.NotificationChannel.EMAIL
               and notification.status = com.synapse.platform.notification.entity.NotificationStatus.SENT
               and notification.notificationType <> 'PASSWORD_RESET_CODE'
               and notification.sentAt >= :dayStart
             """)
    long countTodayEmailByUserId(UUID userId, Instant dayStart);
}
