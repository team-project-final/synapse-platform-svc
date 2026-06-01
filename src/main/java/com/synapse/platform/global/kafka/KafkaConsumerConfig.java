package com.synapse.platform.global.kafka;

import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

@EnableKafka
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url:http://localhost:8086}")
    private String schemaRegistryUrl;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    @Value("${spring.kafka.consumer.group-id:platform-svc-group}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProperties());
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory,
            @Qualifier("defaultErrorHandler") DefaultErrorHandler errorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setMissingTopicsFatal(false);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean("auditConsumerFactory")
    public ConsumerFactory<String, Object> auditConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProperties());
    }

    @Bean("auditKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> auditKafkaListenerContainerFactory(
            @Qualifier("auditConsumerFactory") ConsumerFactory<String, Object> auditConsumerFactory,
            @Qualifier("defaultErrorHandler") DefaultErrorHandler errorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(auditConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setMissingTopicsFatal(false);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean("notificationConsumerFactory")
    public ConsumerFactory<String, Object> notificationConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(consumerProperties());
    }

    @Bean("notificationKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, Object> notificationKafkaListenerContainerFactory(
            @Qualifier("notificationConsumerFactory")
                    ConsumerFactory<String, Object> notificationConsumerFactory,
            @Qualifier("notificationErrorHandler") DefaultErrorHandler notificationErrorHandler) {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(notificationConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setMissingTopicsFatal(false);
        factory.setCommonErrorHandler(notificationErrorHandler);
        return factory;
    }

    private Map<String, Object> consumerProperties() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, KafkaAvroDeserializer.class);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("specific.avro.reader", true);
        return props;
    }
}
