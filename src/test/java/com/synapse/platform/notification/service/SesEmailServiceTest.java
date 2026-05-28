package com.synapse.platform.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.synapse.platform.notification.exception.SesEmailException;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@ExtendWith(MockitoExtension.class)
class SesEmailServiceTest {

    @Mock
    private SesV2Client sesV2Client;

    @Mock
    private UserApi userApi;

    @Test
    void sendToUser_existingUser_shouldSendEmail() {
        UUID userId = UUID.randomUUID();
        given(userApi.findById(userId))
                .willReturn(Optional.of(new UserInfo(userId, "user@example.com", "User", UUID.randomUUID())));

        service().sendToUser(userId, "Subject", "<p>Hello</p>");

        verify(sesV2Client).sendEmail(any(SendEmailRequest.class));
    }

    @Test
    void sendToUser_missingUser_shouldThrowSesEmailException() {
        UUID userId = UUID.randomUUID();
        given(userApi.findById(userId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service().sendToUser(userId, "Subject", "<p>Hello</p>"))
                .isInstanceOf(SesEmailException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void sendToUser_sesClientThrows_shouldThrowSesEmailException() {
        UUID userId = UUID.randomUUID();
        given(userApi.findById(userId))
                .willReturn(Optional.of(new UserInfo(userId, "user@example.com", "User", UUID.randomUUID())));
        given(sesV2Client.sendEmail(any(SendEmailRequest.class)))
                .willThrow(new IllegalStateException("ses down"));

        assertThatThrownBy(() -> service().sendToUser(userId, "Subject", "<p>Hello</p>"))
                .isInstanceOf(SesEmailException.class)
                .hasMessageContaining("ses down");
    }

    private SesEmailService service() {
        SesEmailService service = new SesEmailService(sesV2Client, userApi);
        ReflectionTestUtils.setField(service, "fromEmail", "noreply@synapse.app");
        return service;
    }
}
