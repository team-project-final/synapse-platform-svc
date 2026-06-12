package com.synapse.platform.auth.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.UserRegistered;
import com.synapse.platform.global.kafka.KafkaTopicProperties;
import com.synapse.platform.global.kafka.KafkaTopicResolver;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class UserEventPublisherTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OutboxEventRepository repository = Mockito.mock(OutboxEventRepository.class);
    private final UserEventPublisher publisher = new UserEventPublisher(repository, topicResolver(""));

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
        assertThat(saved.getEventKey()).isEqualTo(tenantId.toString());
        assertThat(saved.getEventType()).isEqualTo(UserRegistered.class.getName());
        assertThat(saved.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        JsonNode payload = objectMapper.readTree(saved.getPayload());
        assertThat(payload.get("eventId").asText()).isNotBlank();
        assertThat(payload.get("occurredAt").asLong()).isPositive();
        assertThat(payload.get("tenantId").asText()).isEqualTo(tenantId.toString());
        assertThat(payload.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(payload.get("email").asText()).isEqualTo("new@example.com");
        assertThat(payload.get("displayName").asText()).isEqualTo("New User");
    }

    @Test
    void publishUserRegistered_nullTenantId_shouldNotStoreOutboxEvent() {
        publisher.publishUserRegistered(UUID.randomUUID(), "new@example.com", "New User", null);

        verify(repository, never()).save(any());
    }

    @Test
    void publishUserRegistered_shouldUsePrefixedTopic() {
        UserEventPublisher prefixedPublisher = new UserEventPublisher(repository, topicResolver("dev."));
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        when(repository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        prefixedPublisher.publishUserRegistered(userId, "new@example.com", "New User", tenantId);

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("dev.platform.auth.user-registered-v1");
    }

    private KafkaTopicResolver topicResolver(String prefix) {
        KafkaTopicProperties properties = new KafkaTopicProperties();
        properties.setPrefix(prefix);
        return new KafkaTopicResolver(properties);
    }
}
