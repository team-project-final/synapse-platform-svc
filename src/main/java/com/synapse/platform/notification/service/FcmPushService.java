package com.synapse.platform.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.synapse.platform.notification.entity.DeviceToken;
import com.synapse.platform.notification.repository.DeviceTokenRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(FirebaseApp.class)
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

    private final DeviceTokenRepository deviceTokenRepository;

    public FcmPushService(DeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    public int sendToUser(UUID userId, String title, String body, Map<String, String> data) {
        List<String> tokens = deviceTokenRepository.findByUserId(userId).stream()
                .filter(DeviceToken::isActive)
                .map(DeviceToken::getToken)
                .toList();

        if (tokens.isEmpty()) {
            log.debug("No active FCM tokens for user {}", userId);
            return 0;
        }

        MulticastMessage message = MulticastMessage.builder()
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putAllData(data != null ? data : Collections.emptyMap())
                .addAllTokens(tokens)
                .build();

        try {
            BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
            if (response.getFailureCount() > 0) {
                log.warn(
                        "FCM partial failure for user {}: {}/{} succeeded",
                        userId,
                        response.getSuccessCount(),
                        tokens.size());
            }
            return response.getSuccessCount();
        } catch (Exception exception) {
            throw new RuntimeException("FCM multicast failed for user " + userId, exception);
        }
    }
}
