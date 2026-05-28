package com.synapse.platform.auth.event;

import com.synapse.platform.global.kafka.event.PlatformAvroEvents;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class UserEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(UserEventPublisher.class);

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
            byte[] payload = PlatformAvroEvents.userRegisteredEnvelopeBytes(
                    userId,
                    email,
                    displayName,
                    tenantId);
            outboxEventRepository.save(OutboxEvent.pending(
                    userRegisteredTopic,
                    userId.toString(),
                    PlatformAvroEvents.USER_REGISTERED_TYPE,
                    payload));
        } catch (RuntimeException exception) {
            log.error("Failed to build user registered event: userId={}", userId, exception);
        }
    }
}
