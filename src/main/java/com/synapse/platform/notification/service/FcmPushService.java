package com.synapse.platform.notification.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.synapse.platform.notification.entity.DeviceToken;
import com.synapse.platform.notification.repository.DeviceTokenRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBean(FirebaseApp.class)
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);
    private static final String METRIC = "notification.send";
    private static final String LATENCY_METRIC = "notification.send.latency";
    private static final String CHANNEL = "fcm";

    private final DeviceTokenRepository deviceTokenRepository;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final long backoffMs;

    public FcmPushService(
            DeviceTokenRepository deviceTokenRepository,
            MeterRegistry meterRegistry,
            @Value("${app.notification.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.notification.retry.backoff-ms:200}") long backoffMs) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
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

        Timer.Sample sample = Timer.start(meterRegistry);
        BatchResponse response;
        try {
            response = sendWithRetry(message);
            sample.stop(meterRegistry.timer(LATENCY_METRIC, "channel", CHANNEL));
        } catch (Exception exception) {
            sample.stop(meterRegistry.timer(LATENCY_METRIC, "channel", CHANNEL));
            meterRegistry.counter(METRIC, "channel", CHANNEL, "result", "error").increment();
            throw new RuntimeException("FCM multicast failed for user " + userId, exception);
        }

        int success = response.getSuccessCount();
        int failure = response.getFailureCount();
        if (success > 0) {
            meterRegistry.counter(METRIC, "channel", CHANNEL, "result", "success").increment(success);
        }
        if (failure > 0) {
            meterRegistry.counter(METRIC, "channel", CHANNEL, "result", "failure").increment(failure);
            log.warn("FCM delivery failure for user {}: {}/{} succeeded", userId, success, tokens.size());
        }
        if (success == 0 && failure > 0) {
            throw new RuntimeException(
                    "FCM multicast delivered to 0 of " + tokens.size() + " tokens for user " + userId);
        }
        return success;
    }

    private BatchResponse sendWithRetry(MulticastMessage message) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return FirebaseMessaging.getInstance().sendEachForMulticast(message);
            } catch (Exception exception) {
                last = exception;
                log.warn("FCM send attempt {}/{} failed: {}", attempt, maxAttempts, exception.getMessage());
                if (attempt < maxAttempts) {
                    sleep(backoffMs * attempt);
                }
            }
        }
        throw last;
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
