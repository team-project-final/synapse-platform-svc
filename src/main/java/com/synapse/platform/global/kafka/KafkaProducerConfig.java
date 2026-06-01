package com.synapse.platform.global.kafka;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url:http://localhost:8086}")
    private String schemaRegistryUrl;

    @Value("${spring.kafka.producer.acks:all}")
    private String acks;

    @Value("${spring.kafka.properties.auto.register.schemas:true}")
    private boolean autoRegisterSchemas;

    @Bean("eventKafkaTemplate")
    public KafkaTemplate<String, Object> eventKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put("schema.registry.url", schemaRegistryUrl);
        props.put("auto.register.schemas", autoRegisterSchemas);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }
}
