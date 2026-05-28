package com.synapse.platform.auth.event;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            OutboxEventStatus status,
            OffsetDateTime nextAttemptAt);

    default int claimPending(UUID id, OffsetDateTime leaseExpiresAt) {
        return claim(id, OutboxEventStatus.PUBLISHING, OutboxEventStatus.PENDING, leaseExpiresAt);
    }

    default int resetTimedOutPublishing(OffsetDateTime now) {
        return resetTimedOutPublishing(OutboxEventStatus.PENDING, OutboxEventStatus.PUBLISHING, now);
    }

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEvent event
               set event.status = :publishing
                 , event.nextAttemptAt = :leaseExpiresAt
             where event.id = :id
               and event.status = :pending
            """)
    int claim(
            @Param("id") UUID id,
            @Param("publishing") OutboxEventStatus publishing,
            @Param("pending") OutboxEventStatus pending,
            @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update OutboxEvent event
               set event.status = :pending
             where event.status = :publishing
               and event.nextAttemptAt <= :now
            """)
    int resetTimedOutPublishing(
            @Param("pending") OutboxEventStatus pending,
            @Param("publishing") OutboxEventStatus publishing,
            @Param("now") OffsetDateTime now);
}
