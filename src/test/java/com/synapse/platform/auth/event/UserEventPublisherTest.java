package com.synapse.platform.auth.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import java.util.UUID;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class UserEventPublisherTest {

    private final OutboxEventRepository repository = Mockito.mock(OutboxEventRepository.class);
    private final UserEventPublisher publisher = new UserEventPublisher(
            repository,
            "platform.auth.user-registered-v1");

    @Test
    void publishUserRegistered_shouldStorePendingOutboxEvent() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        publisher.publishUserRegistered(userId, "new@example.com", "New User", tenantId);

        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getTopic()).isEqualTo("platform.auth.user-registered-v1");
        assertThat(saved.getEventKey()).isEqualTo(userId.toString());
        assertThat(saved.getEventType()).isEqualTo("com.synapse.event.platform.UserRegistered");
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        GenericRecord envelope = PlatformAvroEvents.decodeCloudEvent(saved.getPayload());
        GenericRecord data = PlatformAvroEvents.decodeUserRegistered(envelope);
        assertThat(envelope.get("type").toString()).isEqualTo("com.synapse.event.platform.UserRegistered");
        assertThat(envelope.get("tenantid").toString()).isEqualTo(tenantId.toString());
        assertThat(data.get("userId").toString()).isEqualTo(userId.toString());
        assertThat(data.get("tenantId").toString()).isEqualTo(tenantId.toString());
        assertThat(data.get("email").toString()).isEqualTo("new@example.com");
    }

    @Test
    void publishUserRegistered_nullTenantId_shouldNotStoreOutboxEvent() {
        publisher.publishUserRegistered(UUID.randomUUID(), "new@example.com", "New User", null);

        verify(repository, never()).save(any());
    }
}
