package com.synapse.platform.notification.repository;

import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByEventIdAndChannel(UUID eventId, NotificationChannel channel);

    @Query("""
            select count(notification)
              from Notification notification
             where notification.userId = :userId
               and notification.channel = com.synapse.platform.notification.entity.NotificationChannel.EMAIL
               and notification.status = com.synapse.platform.notification.entity.NotificationStatus.SENT
               and notification.sentAt >= :dayStart
            """)
    long countTodayEmailByUserId(UUID userId, Instant dayStart);
}
