package com.synapse.platform.global.kafka.event;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;

public final class PlatformAvroEvents {

    public static final String USER_REGISTERED_TYPE = "com.synapse.event.platform.UserRegistered";
    public static final String NOTIFICATION_SEND_TYPE = "com.synapse.event.platform.NotificationSend";

    private static final Schema CLOUD_EVENT_SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "CloudEventEnvelope",
              "namespace": "com.synapse.event.shared",
              "fields": [
                {"name": "specversion", "type": "string", "default": "1.0"},
                {"name": "id", "type": "string", "default": ""},
                {"name": "source", "type": "string", "default": ""},
                {"name": "type", "type": "string", "default": ""},
                {"name": "time", "type": {"type": "long", "logicalType": "timestamp-millis"}, "default": 0},
                {"name": "tenantid", "type": "string", "default": ""},
                {"name": "datacontenttype", "type": "string", "default": "application/json"},
                {"name": "traceparent", "type": ["null", "string"], "default": null},
                {"name": "data", "type": "bytes", "default": ""}
              ]
            }
            """);

    private static final Schema USER_REGISTERED_SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "UserRegistered",
              "namespace": "com.synapse.event.platform",
              "fields": [
                {"name": "userId", "type": "string", "default": ""},
                {"name": "tenantId", "type": "string", "default": ""},
                {"name": "email", "type": "string", "default": ""},
                {"name": "displayName", "type": "string", "default": ""},
                {"name": "registeredAt", "type": {"type": "long", "logicalType": "timestamp-millis"}, "default": 0}
              ]
            }
            """);

    private static final Schema NOTIFICATION_SEND_SCHEMA = new Schema.Parser().parse("""
            {
              "type": "record",
              "name": "NotificationSend",
              "namespace": "com.synapse.event.platform",
              "fields": [
                {"name": "userId", "type": "string", "default": ""},
                {"name": "tenantId", "type": "string", "default": ""},
                {"name": "notificationType", "type": "string", "default": ""},
                {"name": "channels", "type": {"type": "array", "items": "string"}, "default": []},
                {"name": "title", "type": "string", "default": ""},
                {"name": "body", "type": "string", "default": ""},
                {"name": "emailSubject", "type": ["null", "string"], "default": null},
                {"name": "emailHtmlBody", "type": ["null", "string"], "default": null},
                {"name": "data", "type": {"type": "map", "values": "string"}, "default": {}}
              ]
            }
            """);

    private PlatformAvroEvents() {
    }

    public static GenericRecord userRegisteredEnvelope(
            UUID userId,
            String email,
            String displayName,
            UUID tenantId) {
        long now = Instant.now().toEpochMilli();
        GenericRecord userRegistered = new GenericData.Record(USER_REGISTERED_SCHEMA);
        userRegistered.put("userId", userId.toString());
        userRegistered.put("tenantId", tenantId.toString());
        userRegistered.put("email", email);
        userRegistered.put("displayName", displayName);
        userRegistered.put("registeredAt", now);

        GenericRecord envelope = new GenericData.Record(CLOUD_EVENT_SCHEMA);
        envelope.put("specversion", "1.0");
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("source", "platform-service");
        envelope.put("type", USER_REGISTERED_TYPE);
        envelope.put("time", now);
        envelope.put("tenantid", tenantId.toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("traceparent", null);
        envelope.put("data", ByteBuffer.wrap(encode(userRegistered, USER_REGISTERED_SCHEMA)));
        return envelope;
    }

    public static byte[] userRegisteredEnvelopeBytes(
            UUID userId,
            String email,
            String displayName,
            UUID tenantId) {
        return encode(userRegisteredEnvelope(userId, email, displayName, tenantId), CLOUD_EVENT_SCHEMA);
    }

    public static byte[] encodeCloudEvent(GenericRecord envelope) {
        return encode(envelope, CLOUD_EVENT_SCHEMA);
    }

    public static GenericRecord decodeCloudEvent(byte[] bytes) {
        return decode(bytes, CLOUD_EVENT_SCHEMA);
    }

    public static GenericRecord decodeUserRegistered(GenericRecord envelope) {
        Object data = envelope.get("data");
        if (!(data instanceof ByteBuffer buffer)) {
            throw new IllegalArgumentException("CloudEvent data must be bytes");
        }
        return decode(toByteArray(buffer), USER_REGISTERED_SCHEMA);
    }

    public static GenericRecord notificationSendEnvelope(
            UUID userId,
            UUID tenantId,
            String notificationType,
            List<String> channels,
            String title,
            String body,
            String emailSubject,
            String emailHtmlBody) {
        long now = Instant.now().toEpochMilli();
        GenericRecord payload = new GenericData.Record(NOTIFICATION_SEND_SCHEMA);
        payload.put("userId", userId.toString());
        payload.put("tenantId", tenantId.toString());
        payload.put("notificationType", notificationType);
        payload.put("channels", channels);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("emailSubject", emailSubject);
        payload.put("emailHtmlBody", emailHtmlBody);
        payload.put("data", new HashMap<String, String>());

        GenericRecord envelope = new GenericData.Record(CLOUD_EVENT_SCHEMA);
        envelope.put("specversion", "1.0");
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("source", "learning-service");
        envelope.put("type", NOTIFICATION_SEND_TYPE);
        envelope.put("time", now);
        envelope.put("tenantid", tenantId.toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("traceparent", null);
        envelope.put("data", ByteBuffer.wrap(encode(payload, NOTIFICATION_SEND_SCHEMA)));
        return envelope;
    }

    public static GenericRecord decodeNotificationSend(GenericRecord envelope) {
        Object data = envelope.get("data");
        if (!(data instanceof ByteBuffer buffer)) {
            throw new IllegalArgumentException("CloudEvent data must be bytes");
        }
        return decode(toByteArray(buffer), NOTIFICATION_SEND_SCHEMA);
    }

    private static byte[] encode(GenericRecord record, Schema schema) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            var encoder = EncoderFactory.get().binaryEncoder(output, null);
            new GenericDatumWriter<GenericRecord>(schema).write(record, encoder);
            encoder.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to encode Avro event", exception);
        }
    }

    private static GenericRecord decode(byte[] bytes, Schema schema) {
        try {
            var decoder = DecoderFactory.get().binaryDecoder(bytes, null);
            return new GenericDatumReader<GenericRecord>(schema).read(null, decoder);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to decode Avro event", exception);
        }
    }

    private static byte[] toByteArray(ByteBuffer buffer) {
        ByteBuffer copy = buffer.asReadOnlyBuffer();
        byte[] bytes = new byte[copy.remaining()];
        copy.get(bytes);
        return bytes;
    }
}
