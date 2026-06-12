package com.synapse.platform.global.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.audit.consumer.AuditKafkaConsumer;
import com.synapse.platform.notification.consumer.NotificationKafkaConsumer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.annotation.KafkaListener;
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
    private ConsumerFactory<String, Object> auditConsumerFactory;

    @Autowired
    @Qualifier("notificationConsumerFactory")
    private ConsumerFactory<String, Object> notificationConsumerFactory;

    @Autowired
    @Qualifier("eventKafkaTemplate")
    private KafkaTemplate<String, Object> eventKafkaTemplate;

    @Autowired
    @Qualifier("dltKafkaTemplate")
    private KafkaTemplate<String, Object> dltKafkaTemplate;

    @Autowired
    @Qualifier("notificationErrorHandler")
    private DefaultErrorHandler notificationErrorHandler;

    @Autowired
    private KafkaTopicProperties kafkaTopicProperties;

    @Autowired
    private KafkaTopicResolver kafkaTopicResolver;

    @Test
    void kafkaListenerContainerFactory_isConfiguredWithRecordAckMode() {
        assertThat(kafkaListenerContainerFactory).isNotNull();
        assertThat(kafkaListenerContainerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    @Test
    void kafkaTopicProperties_hasDefaultDlqSuffix() {
        assertThat(kafkaTopicProperties.getDlqSuffix()).isEqualTo(".dlq");
    }

    @Test
    void kafkaTopicResolver_usesUnprefixedTopicsByDefault() {
        assertThat(kafkaTopicResolver.userRegistered()).isEqualTo("platform.auth.user-registered-v1");
        assertThat(kafkaTopicResolver.notificationSend())
                .isEqualTo("platform.notification.notification-send-v1");
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
    void eventKafkaTemplate_shouldAutoRegisterSchemasAndRequireAllAcks() {
        var producerFactory = (DefaultKafkaProducerFactory<?, ?>) eventKafkaTemplate.getProducerFactory();

        assertThat(producerFactory.getConfigurationProperties())
                .containsEntry("auto.register.schemas", true)
                .containsEntry("schema.registry.url", "mock://platform-test")
                .containsEntry(ProducerConfig.ACKS_CONFIG, "all");
    }

    @Test
    void dltKafkaTemplate_acceptsSpecificRecords() {
        var producerFactory = (DefaultKafkaProducerFactory<?, ?>) dltKafkaTemplate.getProducerFactory();

        assertThat(producerFactory.getConfigurationProperties())
                .containsEntry("auto.register.schemas", true)
                .containsEntry("schema.registry.url", "mock://platform-test");
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
        assertThat(configuration(consumerFactory))
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "platform-svc-group")
                .containsEntry("schema.registry.url", "mock://platform-test")
                .containsEntry("specific.avro.reader", true);
    }

    @Test
    void auditConsumerFactory_wrapsAvroDeserializerWithErrorHandlingDeserializer() {
        assertErrorHandlingDeserializerConfiguration(auditConsumerFactory);
        assertThat(configuration(auditConsumerFactory))
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "platform-svc-group")
                .containsEntry("schema.registry.url", "mock://platform-test")
                .containsEntry("specific.avro.reader", true);
    }

    @Test
    void notificationConsumerFactory_wrapsAvroDeserializerWithErrorHandlingDeserializer() {
        assertErrorHandlingDeserializerConfiguration(notificationConsumerFactory);
        assertThat(configuration(notificationConsumerFactory))
                .containsEntry(ConsumerConfig.GROUP_ID_CONFIG, "platform-svc-group")
                .containsEntry("schema.registry.url", "mock://platform-test")
                .containsEntry("specific.avro.reader", true);
    }

    @Test
    void kafkaListeners_usePlatformSvcGroup() throws NoSuchMethodException {
        assertThat(listenerGroup(AuditKafkaConsumer.class, "consume"))
                .isEqualTo("platform-svc-group");
        assertThat(listenerGroup(NotificationKafkaConsumer.class, "consume"))
                .isEqualTo("platform-svc-group");
    }

    @Test
    void kafkaListeners_useTopicResolverExpressions() {
        assertThat(listenerTopics(NotificationKafkaConsumer.class, "consume"))
                .containsExactly("#{@kafkaTopicResolver.notificationSend()}");
        assertThat(listenerTopics(AuditKafkaConsumer.class, "consume"))
                .containsExactly("#{@kafkaTopicResolver.userRegistered()}");
        assertThat(listenerTopics(AuditKafkaConsumer.class, "consumeNotificationSend"))
                .containsExactly("#{@kafkaTopicResolver.notificationSend()}");
    }

    private void assertErrorHandlingDeserializerConfiguration(ConsumerFactory<?, ?> factory) {
        assertThat(configuration(factory))
                .containsEntry(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class)
                .containsEntry(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class)
                .containsEntry(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class)
                .containsEntry(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
    }

    private java.util.Map<String, Object> configuration(ConsumerFactory<?, ?> factory) {
        assertThat(factory).isInstanceOf(DefaultKafkaConsumerFactory.class);
        DefaultKafkaConsumerFactory<?, ?> defaultFactory = (DefaultKafkaConsumerFactory<?, ?>) factory;
        return defaultFactory.getConfigurationProperties();
    }

    private String listenerGroup(Class<?> consumerClass, String methodName) {
        KafkaListener listener = java.util.Arrays.stream(consumerClass.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .filter(method -> method.getParameterCount() == 1)
                .findFirst()
                .orElseThrow()
                .getAnnotation(KafkaListener.class);
        return listener.groupId();
    }

    private String[] listenerTopics(Class<?> consumerClass, String methodName) {
        KafkaListener listener = java.util.Arrays.stream(consumerClass.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .filter(method -> method.getParameterCount() == 1)
                .findFirst()
                .orElseThrow()
                .getAnnotation(KafkaListener.class);
        return listener.topics();
    }
}
