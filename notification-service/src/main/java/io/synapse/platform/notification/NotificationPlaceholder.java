package io.synapse.platform.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationPlaceholder {

    @GetMapping("/notification/health")
    public String health() {
        return "notification-service placeholder";
    }
}
