package com.synapse.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.synapse.platform.user.api.EmailPasswordUserCreateCommand;
import com.synapse.platform.user.api.LoginFailureResult;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserLoginCredential;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserSettings;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.user.repository.UserSettingsRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createForEmailPassword_shouldPersistUserAndDefaultSettings() {
        UUID tenantId = UUID.randomUUID();
        EmailPasswordUserCreateCommand command = new EmailPasswordUserCreateCommand(
                "user@example.com",
                "user",
                "$2a$10$hash",
                tenantId);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        UserInfo result = userService.createForEmailPassword(command);

        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.displayName()).isEqualTo("user");
        assertThat(result.defaultTenantId()).isEqualTo(tenantId);
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    void findLoginCredentialByEmail_shouldExposePasswordHashAndLockState() {
        User user = User.ofEmailPassword("user@example.com", "user", "$2a$10$hash", UUID.randomUUID());
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(user));

        Optional<UserLoginCredential> result = userService.findLoginCredentialByEmail("user@example.com");

        assertThat(result).isPresent();
        assertThat(result.get().email()).isEqualTo("user@example.com");
        assertThat(result.get().passwordHash()).isEqualTo("$2a$10$hash");
        assertThat(result.get().failedLoginCount()).isZero();
        assertThat(result.get().lockedUntil()).isNull();
    }

    @Test
    void recordFailedLogin_fifthFailure_shouldLockForFifteenMinutes() {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-26T10:00:00+09:00");
        User user = User.ofEmailPassword("user@example.com", "user", "$2a$10$hash", UUID.randomUUID());
        for (int count = 0; count < 4; count++) {
            user.recordFailedLogin(now);
        }
        given(userRepository.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));

        LoginFailureResult result = userService.recordFailedLogin(user.getId(), now);

        assertThat(result.failedLoginCount()).isEqualTo(5);
        assertThat(result.locked()).isTrue();
        assertThat(result.lockedUntil()).isEqualTo(now.plusMinutes(15));
        verify(userRepository).findByIdForUpdate(user.getId());
    }

    @Test
    void recordSuccessfulLogin_shouldResetFailureAndLockFields() {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-26T10:00:00+09:00");
        User user = User.ofEmailPassword("user@example.com", "user", "$2a$10$hash", UUID.randomUUID());
        user.recordFailedLogin(now);
        given(userRepository.findByIdForUpdate(user.getId())).willReturn(Optional.of(user));

        userService.recordSuccessfulLogin(user.getId(), now);

        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        assertThat(user.getLastLoginAt()).isEqualTo(now);
        verify(userRepository).findByIdForUpdate(user.getId());
    }
}
