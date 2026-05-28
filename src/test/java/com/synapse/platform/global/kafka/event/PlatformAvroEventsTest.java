package com.synapse.platform.global.kafka.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Test;

class PlatformAvroEventsTest {

    @Test
    void notificationSendEnvelope_shouldEncodeAndDecodePayload() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        GenericRecord envelope = PlatformAvroEvents.notificationSendEnvelope(
                userId,
                tenantId,
                "CARD_REVIEW_DUE",
                List.of("FCM", "EMAIL"),
                "Review due",
                "A card is ready for review.",
                "Synapse review due",
                "<p>A card is ready for review.</p>");

        assertThat(envelope.get("type").toString()).isEqualTo(PlatformAvroEvents.NOTIFICATION_SEND_TYPE);
        assertThat(envelope.get("tenantid").toString()).isEqualTo(tenantId.toString());

        GenericRecord payload = PlatformAvroEvents.decodeNotificationSend(envelope);
        assertThat(payload.get("userId").toString()).isEqualTo(userId.toString());
        assertThat(payload.get("tenantId").toString()).isEqualTo(tenantId.toString());
        assertThat(payload.get("notificationType").toString()).isEqualTo("CARD_REVIEW_DUE");
        Iterable<?> channels = (Iterable<?>) payload.get("channels");
        assertThat(StreamSupport.stream(channels.spliterator(), false)
                .map(Object::toString)
                .toList())
                .containsExactly("FCM", "EMAIL");
        assertThat(payload.get("title").toString()).isEqualTo("Review due");
        assertThat(payload.get("body").toString()).isEqualTo("A card is ready for review.");
        assertThat(payload.get("emailSubject").toString()).isEqualTo("Synapse review due");
        assertThat(payload.get("emailHtmlBody").toString()).isEqualTo("<p>A card is ready for review.</p>");
    }
}
