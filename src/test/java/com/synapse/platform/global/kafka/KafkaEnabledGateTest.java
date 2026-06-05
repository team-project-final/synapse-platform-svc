package com.synapse.platform.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.audit.consumer.AuditKafkaConsumer;
import com.synapse.platform.auth.event.OutboxEventPublisher;
import com.synapse.platform.notification.consumer.NotificationKafkaConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * synapse.kafka.enabled=false 게이트 시 Kafka 인프라/리스너/outbox publisher 빈이
 * 컨텍스트에 생성되지 않음을 검증한다 (KAFKA_ENABLED env 정합, #59).
 */
@SpringBootTest(properties = "synapse.kafka.enabled=false")
@ActiveProfiles("test")
class KafkaEnabledGateTest {

    @Autowired
    private ApplicationContext ctx;

    @Test
    void kafkaBeansAbsent_whenGateDisabled() {
        assertThat(ctx.getBeanNamesForType(KafkaProducerConfig.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(KafkaConsumerConfig.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(KafkaErrorHandlerConfig.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(AuditKafkaConsumer.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(NotificationKafkaConsumer.class)).isEmpty();
        assertThat(ctx.getBeanNamesForType(OutboxEventPublisher.class)).isEmpty();
        assertThat(ctx.containsBean("eventKafkaTemplate")).isFalse();
    }
}
