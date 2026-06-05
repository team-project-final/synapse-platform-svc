package com.synapse.platform.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.CommonClientConfigs;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Verifies security.protocol wiring into the manually-built Kafka producer/consumer
 * factory props (MSK TLS-only readiness, issue #51 / WS3-A).
 *
 * <p>SSL 주입 시 factory config에 security.protocol=SSL 포함, 미주입(PLAINTEXT) 시 미포함(회귀 없음).
 */
class KafkaSecurityProtocolTest {

    @Test
    void producerProperties_includesSecurityProtocol_whenSsl() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9094");
        ReflectionTestUtils.setField(config, "securityProtocol", "SSL");

        assertThat(config.producerProperties())
                .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
    }

    @Test
    void producerProperties_omitsSecurityProtocol_whenPlaintext() {
        KafkaProducerConfig config = new KafkaProducerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "securityProtocol", "PLAINTEXT");

        assertThat(config.producerProperties())
                .doesNotContainKey(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG);
    }

    @Test
    void consumerProperties_includesSecurityProtocol_whenSsl() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9094");
        ReflectionTestUtils.setField(config, "groupId", "platform-svc-group");
        ReflectionTestUtils.setField(config, "autoOffsetReset", "earliest");
        ReflectionTestUtils.setField(config, "securityProtocol", "SSL");

        assertThat(config.consumerProperties())
                .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SSL");
    }

    @Test
    void consumerProperties_omitsSecurityProtocol_whenPlaintext() {
        KafkaConsumerConfig config = new KafkaConsumerConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "groupId", "platform-svc-group");
        ReflectionTestUtils.setField(config, "autoOffsetReset", "earliest");
        ReflectionTestUtils.setField(config, "securityProtocol", "PLAINTEXT");

        assertThat(config.consumerProperties())
                .doesNotContainKey(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG);
    }
}
