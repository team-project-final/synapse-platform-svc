package com.synapse.platform.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class KafkaConsumerConfigSmokeTest {

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory;

    @Autowired
    private KafkaTopicProperties kafkaTopicProperties;

    @Test
    void kafkaListenerContainerFactory_isConfiguredWithRecordAckMode() {
        assertThat(kafkaListenerContainerFactory).isNotNull();
        assertThat(kafkaListenerContainerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    @Test
    void kafkaTopicProperties_hasDefaultDlqSuffix() {
        assertThat(kafkaTopicProperties.getDlqSuffix()).isEqualTo(".DLT");
    }
}
