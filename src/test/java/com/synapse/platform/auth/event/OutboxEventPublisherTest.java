package com.synapse.platform.auth.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.synapse.platform.UserRegistered;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxEventPublisherTest {

    private final OutboxEventRepository repository = Mockito.mock(OutboxEventRepository.class);
    private final KafkaTemplate<String, Object> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
    private final OutboxEventPublisher publisher = new OutboxEventPublisher(repository, kafkaTemplate);

    @Test
    void publishPendingEvents_sendSuccess_shouldMarkEventPublished() {
        UUID tenantId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                tenantId.toString(),
                UserRegistered.class.getName(),
                payload(UUID.randomUUID(), tenantId));
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of(event));
        given(repository.claimPending(eq(event.getId()), any(OffsetDateTime.class))).willReturn(1);
        given(kafkaTemplate.send(eq(event.getTopic()), eq(tenantId.toString()), any(UserRegistered.class)))
                .willReturn(CompletableFuture.completedFuture(Mockito.mock(SendResult.class)));

        publisher.publishPendingEvents();

        verify(repository).resetTimedOutPublishing(any(OffsetDateTime.class));
        verify(repository).claimPending(eq(event.getId()), any(OffsetDateTime.class));
        ArgumentCaptor<UserRegistered> recordCaptor = ArgumentCaptor.forClass(UserRegistered.class);
        verify(kafkaTemplate).send(eq(event.getTopic()), eq(tenantId.toString()), recordCaptor.capture());
        assertThat(recordCaptor.getValue().getTenantId().toString()).isEqualTo(tenantId.toString());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void publishPendingEvents_claimFailure_shouldNotPublishEvent() {
        OutboxEvent event = OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                UUID.randomUUID().toString(),
                UserRegistered.class.getName(),
                payload(UUID.randomUUID(), UUID.randomUUID()));
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of(event));
        given(repository.claimPending(eq(event.getId()), any(OffsetDateTime.class))).willReturn(0);

        publisher.publishPendingEvents();

        verify(repository).claimPending(eq(event.getId()), any(OffsetDateTime.class));
        verify(kafkaTemplate, Mockito.never()).send(any(), any(), any(UserRegistered.class));
    }

    @Test
    void publishPendingEvents_sendFailure_shouldRecordFailureForRetry() {
        UUID tenantId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                tenantId.toString(),
                UserRegistered.class.getName(),
                payload(UUID.randomUUID(), tenantId));
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of(event));
        given(repository.claimPending(eq(event.getId()), any(OffsetDateTime.class))).willReturn(1);
        given(kafkaTemplate.send(eq(event.getTopic()), eq(tenantId.toString()), any(UserRegistered.class)))
                .willReturn(failed);

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isOne();
        assertThat(event.getLastError()).contains("broker unavailable");
        assertThat(event.getNextAttemptAt()).isAfter(OffsetDateTime.now());
        verify(repository).save(event);
    }

    @Test
    void publishPendingEvents_sendThrows_shouldRecordFailureForRetry() {
        UUID tenantId = UUID.randomUUID();
        OutboxEvent event = OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                tenantId.toString(),
                UserRegistered.class.getName(),
                payload(UUID.randomUUID(), tenantId));
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of(event));
        given(repository.claimPending(eq(event.getId()), any(OffsetDateTime.class))).willReturn(1);
        willThrow(new IllegalStateException("serializer unavailable"))
                .given(kafkaTemplate)
                .send(eq(event.getTopic()), eq(tenantId.toString()), any(UserRegistered.class));

        publisher.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isOne();
        assertThat(event.getLastError()).contains("serializer unavailable");
        verify(repository).save(event);
    }

    @Test
    void publishPendingEvents_shouldRecoverTimedOutPublishingEventsBeforeQueryingPendingEvents() {
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of());

        publisher.publishPendingEvents();

        InOrder inOrder = Mockito.inOrder(repository);
        inOrder.verify(repository).resetTimedOutPublishing(any(OffsetDateTime.class));
        inOrder.verify(repository).findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class));
    }

    @Test
    void publishPendingEvents_claimShouldUseFutureLeaseDeadline() {
        OutboxEvent event = OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                UUID.randomUUID().toString(),
                UserRegistered.class.getName(),
                payload(UUID.randomUUID(), UUID.randomUUID()));
        OffsetDateTime beforePublish = OffsetDateTime.now();
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of(event));
        given(repository.claimPending(eq(event.getId()), any(OffsetDateTime.class))).willReturn(0);

        publisher.publishPendingEvents();

        ArgumentCaptor<OffsetDateTime> leaseDeadline = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repository).claimPending(eq(event.getId()), leaseDeadline.capture());
        assertThat(leaseDeadline.getValue()).isAfter(beforePublish);
    }

    private byte[] payload(UUID userId, UUID tenantId) {
        String payload = """
                {
                  "eventId": "%s",
                  "tenantId": "%s",
                  "occurredAt": 1717000000000,
                  "userId": "%s",
                  "email": "new@example.com",
                  "displayName": "New User"
                }
                """.formatted(UUID.randomUUID(), tenantId, userId);
        return payload.getBytes(StandardCharsets.UTF_8);
    }
}
