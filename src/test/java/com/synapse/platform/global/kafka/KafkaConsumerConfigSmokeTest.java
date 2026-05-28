package com.synapse.platform.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class KafkaConsumerConfigSmokeTest {

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<?, ?> kafkaListenerContainerFactory;

    @Autowired
    @Qualifier("auditKafkaListenerContainerFactory")
    private ConcurrentKafkaListenerContainerFactory<?, ?> auditKafkaListenerContainerFactory;

    @Autowired
    @Qualifier("notificationKafkaListenerContainerFactory")
    private ConcurrentKafkaListenerContainerFactory<?, ?> notificationKafkaListenerContainerFactory;

    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @Autowired
    @Qualifier("auditConsumerFactory")
    private ConsumerFactory<String, GenericRecord> auditConsumerFactory;

    @Autowired
    @Qualifier("notificationConsumerFactory")
    private ConsumerFactory<String, GenericRecord> notificationConsumerFactory;

    @Autowired
    @Qualifier("eventKafkaTemplate")
    private KafkaTemplate<String, GenericRecord> eventKafkaTemplate;

    @Autowired
    @Qualifier("notificationErrorHandler")
    private DefaultErrorHandler notificationErrorHandler;

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

    @Test
    void auditKafkaListenerContainerFactory_isConfiguredWithRecordAckMode() {
        assertThat(auditKafkaListenerContainerFactory).isNotNull();
        assertThat(auditKafkaListenerContainerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    @Test
    void notificationKafkaListenerContainerFactory_isConfiguredWithRecordAckMode() {
        assertThat(notificationKafkaListenerContainerFactory).isNotNull();
        assertThat(notificationKafkaListenerContainerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    @Test
    void eventKafkaTemplate_isAvailableForAvroEvents() {
        assertThat(eventKafkaTemplate).isNotNull();
    }

    @Test
    void kafkaTopicProperties_hasDefaultUserRegisteredTopic() {
        assertThat(kafkaTopicProperties.getUserRegistered()).isEqualTo("platform.auth.user-registered-v1");
    }

    @Test
    void kafkaTopicProperties_hasDefaultNotificationSendTopic() {
        assertThat(kafkaTopicProperties.getNotificationSend())
                .isEqualTo("platform.notification.notification-send-v1");
    }

    @Test
    void notificationErrorHandler_isAvailable() {
        assertThat(notificationErrorHandler).isNotNull();
    }

    @Test
    void consumerFactory_wrapsAvroDeserializerWithErrorHandlingDeserializer() {
        assertErrorHandlingDeserializerConfiguration(consumerFactory);
    }

    @Test
    void auditConsumerFactory_wrapsAvroDeserializerWithErrorHandlingDeserializer() {
        assertErrorHandlingDeserializerConfiguration(auditConsumerFactory);
    }

    @Test
    void notificationConsumerFactory_wrapsAvroDeserializerWithErrorHandlingDeserializer() {
        assertErrorHandlingDeserializerConfiguration(notificationConsumerFactory);
    }

    private void assertErrorHandlingDeserializerConfiguration(ConsumerFactory<?, ?> factory) {
        assertThat(factory).isInstanceOf(DefaultKafkaConsumerFactory.class);
        DefaultKafkaConsumerFactory<?, ?> defaultFactory = (DefaultKafkaConsumerFactory<?, ?>) factory;

        assertThat(defaultFactory.getConfigurationProperties())
                .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class)
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class)
                .containsEntry(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class)
                .containsEntry(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
    }
}
