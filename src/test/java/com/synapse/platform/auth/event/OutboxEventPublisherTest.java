package com.synapse.platform.auth.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class OutboxEventPublisherTest {

    private final OutboxEventRepository repository = Mockito.mock(OutboxEventRepository.class);
    private final KafkaTemplate<String, GenericRecord> kafkaTemplate = Mockito.mock(KafkaTemplate.class);
    private final OutboxEventPublisher publisher = new OutboxEventPublisher(repository, kafkaTemplate);

    @Test
    void publishPendingEvents_sendSuccess_shouldMarkEventPublished() {
        OutboxEvent event = OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                "user-1",
                "com.synapse.event.platform.UserRegistered",
                payload());
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of(event));
        given(repository.claimPending(eq(event.getId()), any(OffsetDateTime.class))).willReturn(1);
        given(kafkaTemplate.send(eq(event.getTopic()), eq(event.getEventKey()), any(GenericRecord.class)))
                .willReturn(CompletableFuture.completedFuture(Mockito.mock(SendResult.class)));

        publisher.publishPendingEvents();

        verify(repository).resetTimedOutPublishing(any(OffsetDateTime.class));
        verify(repository).claimPending(eq(event.getId()), any(OffsetDateTime.class));
        verify(kafkaTemplate).send(eq(event.getTopic()), eq(event.getEventKey()), any(GenericRecord.class));
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void publishPendingEvents_claimFailure_shouldNotPublishEvent() {
        OutboxEvent event = OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                "user-1",
                "com.synapse.event.platform.UserRegistered",
                payload());
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of(event));
        given(repository.claimPending(eq(event.getId()), any(OffsetDateTime.class))).willReturn(0);

        publisher.publishPendingEvents();

        verify(repository).claimPending(eq(event.getId()), any(OffsetDateTime.class));
        verify(kafkaTemplate, Mockito.never()).send(any(), any(), any(GenericRecord.class));
    }

    @Test
    void publishPendingEvents_sendFailure_shouldRecordFailureForRetry() {
        OutboxEvent event = OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                "user-1",
                "com.synapse.event.platform.UserRegistered",
                payload());
        CompletableFuture<SendResult<String, GenericRecord>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of(event));
        given(repository.claimPending(eq(event.getId()), any(OffsetDateTime.class))).willReturn(1);
        given(kafkaTemplate.send(eq(event.getTopic()), eq(event.getEventKey()), any(GenericRecord.class)))
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
        OutboxEvent event = OutboxEvent.pending(
                "platform.auth.user-registered-v1",
                "user-1",
                "com.synapse.event.platform.UserRegistered",
                payload());
        given(repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                eq(OutboxEventStatus.PENDING),
                any(OffsetDateTime.class)))
                .willReturn(List.of(event));
        given(repository.claimPending(eq(event.getId()), any(OffsetDateTime.class))).willReturn(1);
        willThrow(new IllegalStateException("serializer unavailable"))
                .given(kafkaTemplate)
                .send(eq(event.getTopic()), eq(event.getEventKey()), any(GenericRecord.class));

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
                "user-1",
                "com.synapse.event.platform.UserRegistered",
                payload());
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

    private byte[] payload() {
        return PlatformAvroEvents.userRegisteredEnvelopeBytes(
                java.util.UUID.randomUUID(),
                "new@example.com",
                "New User",
                java.util.UUID.randomUUID());
    }
}
