package com.synapse.platform.notification.consumer;

import com.synapse.platform.NotificationSend;
import com.synapse.platform.notification.service.NotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "synapse.kafka", name = "enabled", havingValue = "true")
public class NotificationKafkaConsumer {

    private final NotificationService notificationService;

    public NotificationKafkaConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.notification-send:platform.notification.notification-send-v1}",
            groupId = "platform-svc-group",
            containerFactory = "notificationKafkaListenerContainerFactory")
    public void consume(NotificationSend event) {
        notificationService.processNotificationSend(event);
    }
}
