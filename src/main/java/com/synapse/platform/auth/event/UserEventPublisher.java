package com.synapse.platform.auth.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.UserRegistered;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OutboxEventRepository outboxEventRepository;
    private final String userRegisteredTopic;

    public UserEventPublisher(
            OutboxEventRepository outboxEventRepository,
            @Value("${app.kafka.topics.user-registered:platform.auth.user-registered-v1}")
                    String userRegisteredTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.userRegisteredTopic = userRegisteredTopic;
    }

    public void publishUserRegistered(UUID userId, String email, String displayName, UUID tenantId) {
        if (tenantId == null) {
            log.warn("User registered event skipped because tenantId is null: userId={}", userId);
            return;
        }

        try {
            UserRegisteredOutboxPayload eventPayload = UserRegisteredOutboxPayload.create(
                    userId, email, displayName, tenantId);
            byte[] payload = objectMapper.writeValueAsBytes(eventPayload);
            outboxEventRepository.save(OutboxEvent.pending(
                    userRegisteredTopic,
                    tenantId.toString(),
                    UserRegistered.class.getName(),
                    payload));
        } catch (JsonProcessingException exception) {
            log.error("Failed to build user registered event: userId={}", userId, exception);
        }
    }
}
