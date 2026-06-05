package com.synapse.platform.notification.service;

import com.synapse.platform.notification.exception.SesEmailException;
import com.synapse.platform.user.api.UserApi;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Service
@ConditionalOnBean(SesV2Client.class)
public class SesEmailService {

    private static final Logger log = LoggerFactory.getLogger(SesEmailService.class);
    private static final String METRIC = "notification.send";
    private static final String LATENCY_METRIC = "notification.send.latency";
    private static final String CHANNEL = "ses";

    private final SesV2Client sesV2Client;
    private final UserApi userApi;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final long backoffMs;

    @Value("${app.ses.from-email:noreply@synapse.app}")
    private String fromEmail;

    public SesEmailService(
            SesV2Client sesV2Client,
            UserApi userApi,
            MeterRegistry meterRegistry,
            @Value("${app.notification.retry.max-attempts:3}") int maxAttempts,
            @Value("${app.notification.retry.backoff-ms:200}") long backoffMs) {
        this.sesV2Client = sesV2Client;
        this.userApi = userApi;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = maxAttempts;
        this.backoffMs = backoffMs;
    }

    public void sendToUser(UUID userId, String subject, String htmlBody) {
        String email = userApi.findById(userId)
                .map(user -> user.email())
                .orElseThrow(() -> new SesEmailException("User not found: " + userId));

        SendEmailRequest request = SendEmailRequest.builder()
                .fromEmailAddress(fromEmail)
                .destination(Destination.builder().toAddresses(email).build())
                .content(EmailContent.builder()
                        .simple(Message.builder()
                                .subject(Content.builder().data(subject).charset("UTF-8").build())
                                .body(Body.builder()
                                        .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                        .build())
                                .build())
                        .build())
                .build();

        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            sendWithRetry(request);
            sample.stop(meterRegistry.timer(LATENCY_METRIC, "channel", CHANNEL));
            meterRegistry.counter(METRIC, "channel", CHANNEL, "result", "success").increment();
        } catch (Exception exception) {
            sample.stop(meterRegistry.timer(LATENCY_METRIC, "channel", CHANNEL));
            meterRegistry.counter(METRIC, "channel", CHANNEL, "result", "error").increment();
            log.error("SES send failed for user {}: {}", userId, exception.getMessage());
            throw new SesEmailException(exception.getMessage());
        }
    }

    private void sendWithRetry(SendEmailRequest request) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                sesV2Client.sendEmail(request);
                return;
            } catch (Exception exception) {
                last = exception;
                log.warn("SES send attempt {}/{} failed: {}", attempt, maxAttempts, exception.getMessage());
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
