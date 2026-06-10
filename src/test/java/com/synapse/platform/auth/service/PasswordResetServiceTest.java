package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.entity.PasswordResetRequest;
import com.synapse.platform.auth.exception.PasswordResetException;
import com.synapse.platform.auth.repository.PasswordResetRequestRepository;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserLoginCredential;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserApi userApi;

    @Mock
    private PasswordResetRequestRepository passwordResetRequestRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordResetCodeSender passwordResetCodeSender;

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Test
    void request_missingEmail_shouldReturnSilentlyWithoutSavingRequest() {
        given(userApi.findLoginCredentialByEmail("missing@example.com")).willReturn(Optional.empty());

        passwordResetService.request(" Missing@Example.com ");

        verify(passwordResetRequestRepository, never()).save(any());
    }

    @Test
    void request_inactiveUser_shouldReturnSilentlyWithoutSavingRequest() {
        UUID userId = UUID.randomUUID();
        given(userApi.findLoginCredentialByEmail("user@example.com"))
                .willReturn(Optional.of(credential(userId, "suspended", "$2a$hash")));

        passwordResetService.request("user@example.com");

        verify(passwordResetRequestRepository, never()).save(any());
    }

    @Test
    void request_oauthOnlyUser_shouldReturnSilentlyWithoutSavingRequest() {
        UUID userId = UUID.randomUUID();
        given(userApi.findLoginCredentialByEmail("user@example.com"))
                .willReturn(Optional.of(credential(userId, "active", null)));

        passwordResetService.request("user@example.com");

        verify(passwordResetRequestRepository, never()).save(any());
    }

    @Test
    void request_activePasswordUser_shouldSaveCodeHashOnly() {
        UUID userId = UUID.randomUUID();
        UserInfo user = user(userId);
        given(userApi.findLoginCredentialByEmail("user@example.com"))
                .willReturn(Optional.of(credential(userId, "active", "$2a$hash")));
        given(userApi.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.encode(anyString())).willReturn("$2a$encoded-code");

        passwordResetService.request(" User@Example.com ");

        ArgumentCaptor<PasswordResetRequest> captor = ArgumentCaptor.forClass(PasswordResetRequest.class);
        verify(passwordResetRequestRepository).save(captor.capture());
        PasswordResetRequest saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getCodeHash()).isEqualTo("$2a$encoded-code");
        assertThat(saved.getStatus()).isEqualTo(PasswordResetRequest.STATUS_PENDING);
        assertThat(saved.getExpiresAt()).isAfter(OffsetDateTime.now());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(codeCaptor.capture());
        verify(passwordResetCodeSender).send(eq(user), eq(codeCaptor.getValue()), any(OffsetDateTime.class));
        assertThat(codeCaptor.getValue()).matches("\\d{6}");
    }

    @Test
    void verify_validCode_shouldReturnResetTokenAndStoreTokenHashOnly() {
        PasswordResetRequest request = resetRequest("123456", OffsetDateTime.now().plusMinutes(10));
        given(passwordResetRequestRepository.findFirstByEmailAndStatusOrderByCreatedAtDesc(
                "user@example.com",
                PasswordResetRequest.STATUS_PENDING)).willReturn(Optional.of(request));
        given(passwordEncoder.matches("123456", "$2a$encoded-code")).willReturn(true);

        PasswordResetResult result = passwordResetService.verify("USER@example.com", "123456");

        assertThat(result.resetToken()).isNotBlank();
        assertThat(result.expiresAt()).isAfter(OffsetDateTime.now());
        assertThat(request.getStatus()).isEqualTo(PasswordResetRequest.STATUS_VERIFIED);
        assertThat(request.getResetTokenHash()).hasSize(64);
        assertThat(request.getResetTokenHash()).isNotEqualTo(result.resetToken());
        assertThat(request.getVerifiedAt()).isNotNull();
    }

    @Test
    void verify_wrongCode_shouldIncrementAttemptAndThrow() {
        PasswordResetRequest request = resetRequest("123456", OffsetDateTime.now().plusMinutes(10));
        given(passwordResetRequestRepository.findFirstByEmailAndStatusOrderByCreatedAtDesc(
                "user@example.com",
                PasswordResetRequest.STATUS_PENDING)).willReturn(Optional.of(request));
        given(passwordEncoder.matches("000000", "$2a$encoded-code")).willReturn(false);

        assertThatThrownBy(() -> passwordResetService.verify("user@example.com", "000000"))
                .isInstanceOf(PasswordResetException.class);

        assertThat(request.getAttempts()).isEqualTo(1);
        assertThat(request.getStatus()).isEqualTo(PasswordResetRequest.STATUS_PENDING);
    }

    @Test
    void verify_expiredCode_shouldMarkExpiredAndThrow() {
        PasswordResetRequest request = resetRequest("123456", OffsetDateTime.now().minusSeconds(1));
        given(passwordResetRequestRepository.findFirstByEmailAndStatusOrderByCreatedAtDesc(
                "user@example.com",
                PasswordResetRequest.STATUS_PENDING)).willReturn(Optional.of(request));

        assertThatThrownBy(() -> passwordResetService.verify("user@example.com", "123456"))
                .isInstanceOf(PasswordResetException.class);

        assertThat(request.getStatus()).isEqualTo(PasswordResetRequest.STATUS_EXPIRED);
    }

    @Test
    void confirm_validResetToken_shouldResetPasswordAndMarkUsed() {
        UUID userId = UUID.randomUUID();
        PasswordResetRequest request = PasswordResetRequest.create(
                userId,
                "user@example.com",
                "123456",
                OffsetDateTime.now().plusMinutes(10));
        request.markVerified("reset-token", OffsetDateTime.now().plusMinutes(15));
        given(passwordResetRequestRepository.findByResetTokenHashAndStatus(
                PasswordResetRequest.hash("reset-token"),
                PasswordResetRequest.STATUS_VERIFIED)).willReturn(Optional.of(request));
        given(userApi.isLoginAllowed(userId)).willReturn(true);

        passwordResetService.confirm(" reset-token ", "Newpass1!");

        verify(userApi).resetPassword(userId, "Newpass1!");
        assertThat(request.getStatus()).isEqualTo(PasswordResetRequest.STATUS_USED);
        assertThat(request.getUsedAt()).isNotNull();
    }

    @Test
    void confirm_disallowedUser_shouldThrowWithoutResettingPassword() {
        UUID userId = UUID.randomUUID();
        PasswordResetRequest request = PasswordResetRequest.create(
                userId,
                "user@example.com",
                "123456",
                OffsetDateTime.now().plusMinutes(10));
        request.markVerified("reset-token", OffsetDateTime.now().plusMinutes(15));
        given(passwordResetRequestRepository.findByResetTokenHashAndStatus(
                PasswordResetRequest.hash("reset-token"),
                PasswordResetRequest.STATUS_VERIFIED)).willReturn(Optional.of(request));
        given(userApi.isLoginAllowed(userId)).willReturn(false);

        assertThatThrownBy(() -> passwordResetService.confirm("reset-token", "Newpass1!"))
                .isInstanceOf(PasswordResetException.class);

        verify(userApi, never()).resetPassword(any(), any());
    }

    private PasswordResetRequest resetRequest(String rawCode, OffsetDateTime expiresAt) {
        return PasswordResetRequest.create(UUID.randomUUID(), "user@example.com", "$2a$encoded-code", expiresAt);
    }

    private UserInfo user(UUID userId) {
        return new UserInfo(userId, "user@example.com", "User", UUID.randomUUID());
    }

    private UserLoginCredential credential(UUID userId, String status, String passwordHash) {
        return new UserLoginCredential(
                userId,
                "user@example.com",
                passwordHash,
                status,
                0,
                null);
    }
}
