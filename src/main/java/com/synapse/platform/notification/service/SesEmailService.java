package com.synapse.platform.notification.service;

import com.synapse.platform.notification.exception.SesEmailException;
import com.synapse.platform.user.api.UserApi;
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

    private final SesV2Client sesV2Client;
    private final UserApi userApi;

    @Value("${app.ses.from-email:noreply@synapse.app}")
    private String fromEmail;

    public SesEmailService(SesV2Client sesV2Client, UserApi userApi) {
        this.sesV2Client = sesV2Client;
        this.userApi = userApi;
    }

    public void sendToUser(UUID userId, String subject, String htmlBody) {
        String email = userApi.findById(userId)
                .map(user -> user.email())
                .orElseThrow(() -> new SesEmailException("User not found: " + userId));

        try {
            sesV2Client.sendEmail(SendEmailRequest.builder()
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
                    .build());
        } catch (SesEmailException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("SES send failed for user {}: {}", userId, exception.getMessage());
            throw new SesEmailException(exception.getMessage());
        }
    }
}
