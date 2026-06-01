package com.synapse.platform.global.kafka.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.NotificationSend;
import com.synapse.platform.UserRegistered;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.junit.jupiter.api.Test;

class PlatformSpecificRecordsTest {

    @Test
    void userRegisteredSpecificRecord_shouldRoundTrip() throws IOException {
        UserRegistered event = UserRegistered.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setTenantId(UUID.randomUUID().toString())
                .setOccurredAt(1717000000000L)
                .setTraceparent(null)
                .setUserId(UUID.randomUUID().toString())
                .setEmail("new@example.com")
                .setDisplayName("New User")
                .build();

        UserRegistered decoded = roundTrip(event, UserRegistered.class);

        assertThat(decoded.getEventId()).isEqualTo(event.getEventId());
        assertThat(decoded.getTenantId()).isEqualTo(event.getTenantId());
        assertThat(decoded.getOccurredAt()).isEqualTo(event.getOccurredAt());
        assertThat(decoded.getEmail()).isEqualTo("new@example.com");
        assertThat(decoded.getDisplayName()).isEqualTo("New User");
    }

    @Test
    void notificationSendSpecificRecord_shouldRoundTrip() throws IOException {
        NotificationSend event = NotificationSend.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setTenantId(UUID.randomUUID().toString())
                .setOccurredAt(1717000000000L)
                .setTraceparent(null)
                .setUserId(UUID.randomUUID().toString())
                .setNotificationType("AI_CARDS_READY")
                .setChannels(List.of("FCM", "EMAIL"))
                .setTitle("Review due")
                .setBody("A card is ready.")
                .setEmailSubject("Review due email")
                .setEmailHtmlBody("<p>A card is ready.</p>")
                .setData(Map.of("deckId", "deck-1"))
                .build();

        NotificationSend decoded = roundTrip(event, NotificationSend.class);

        assertThat(decoded.getEventId()).isEqualTo(event.getEventId());
        assertThat(decoded.getTenantId()).isEqualTo(event.getTenantId());
        assertThat(decoded.getOccurredAt()).isEqualTo(event.getOccurredAt());
        assertThat(decoded.getNotificationType()).isEqualTo("AI_CARDS_READY");
        assertThat(decoded.getChannels()).containsExactly("FCM", "EMAIL");
        assertThat(decoded.getData()).containsEntry("deckId", "deck-1");
    }

    private static <T extends org.apache.avro.specific.SpecificRecord> T roundTrip(
            T event,
            Class<T> type) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var encoder = EncoderFactory.get().binaryEncoder(output, null);
        new SpecificDatumWriter<T>(type).write(event, encoder);
        encoder.flush();
        var decoder = DecoderFactory.get().binaryDecoder(output.toByteArray(), null);
        return new SpecificDatumReader<>(type).read(null, decoder);
    }
}
