package com.synapse.platform.notification.consumer;

import com.synapse.platform.notification.service.NotificationService;
import org.apache.avro.generic.GenericRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;

    public NotificationKafkaConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.notification-send:platform.notification.notification-send-v1}",
            groupId = "notification-consumer-group",
            containerFactory = "notificationKafkaListenerContainerFactory")
    public void consume(GenericRecord envelope) {
        notificationService.processNotificationSend(envelope);
    }
}
