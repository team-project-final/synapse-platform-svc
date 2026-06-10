package com.synapse.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.synapse.platform.user.api.EmailPasswordUserCreateCommand;
import com.synapse.platform.user.api.LoginFailureResult;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserLoginCredential;
import com.synapse.platform.user.api.UserSessionsRevocationRequested;
import com.synapse.platform.user.dto.request.UserProfileUpdateRequest;
import com.synapse.platform.user.dto.response.UserProfileResponse;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserRole;
import com.synapse.platform.user.entity.UserSettings;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.user.repository.UserRoleRepository;
import com.synapse.platform.user.repository.UserSettingsRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

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
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void createForOAuth_shouldPersistDefaultUserRole() {
        com.synapse.platform.user.api.OAuthUserCreateCommand command =
                new com.synapse.platform.user.api.OAuthUserCreateCommand(
                        "oauth@example.com",
                        "oauth",
                        "OAuth User",
                        "https://example.com/avatar.png",
                        UUID.randomUUID());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));

        UserInfo result = userService.createForOAuth(command);

        assertThat(result.email()).isEqualTo("oauth@example.com");
        verify(userSettingsRepository).save(any(UserSettings.class));
        verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    void findRoles_existingRoles_shouldReturnPersistedRoles() {
        UUID userId = UUID.randomUUID();
        given(userRoleRepository.findAllByUserIdOrderByCreatedAtAsc(userId)).willReturn(List.of(
                UserRole.of(userId, "ROLE_USER"),
                UserRole.of(userId, "ROLE_ADMIN")));

        List<String> roles = userService.findRoles(userId);

        assertThat(roles).containsExactly("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void findRoles_missingRoles_shouldFallbackToUserRole() {
        UUID userId = UUID.randomUUID();
        given(userRoleRepository.findAllByUserIdOrderByCreatedAtAsc(userId)).willReturn(List.of());

        List<String> roles = userService.findRoles(userId);

        assertThat(roles).containsExactly("ROLE_USER");
    }

    @Test
    void findSummariesByIds_shouldReturnUserSummaries() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        given(userRepository.findAllById(List.of(userId))).willReturn(List.of(user));

        var summaries = userService.findSummariesByIds(List.of(userId));

        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().id()).isEqualTo(userId);
        assertThat(summaries.getFirst().email()).isEqualTo("user@example.com");
        assertThat(summaries.getFirst().displayName()).isEqualTo("user");
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

    @Test
    void getMyProfile_shouldReturnUserAndSettings() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        UserSettings settings = UserSettings.defaultFor(userId);
        settings.updateLocale("en-US");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userSettingsRepository.findById(userId)).willReturn(Optional.of(settings));

        UserProfileResponse result = userService.getMyProfile(userId);

        assertThat(result.id()).isEqualTo(userId);
        assertThat(result.email()).isEqualTo("user@example.com");
        assertThat(result.displayName()).isEqualTo("user");
        assertThat(result.language()).isEqualTo("en-US");
        assertThat(result.hasPassword()).isTrue();
    }

    @Test
    void updateMyProfile_shouldUpdateDisplayNameAndLocale() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        UserSettings settings = UserSettings.defaultFor(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userSettingsRepository.findById(userId)).willReturn(Optional.of(settings));

        UserProfileResponse result = userService.updateMyProfile(
                userId,
                new UserProfileUpdateRequest(" Updated User ", "en-US"));

        assertThat(user.getDisplayName()).isEqualTo("Updated User");
        assertThat(settings.getLocale()).isEqualTo("en-US");
        assertThat(result.displayName()).isEqualTo("Updated User");
    }

    @Test
    void getNotificationPreferences_existingSettings_shouldReturnStoredJson() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        UserSettings settings = UserSettings.defaultFor(userId);
        settings.updateNotificationPrefs("{\"quietHours\":{\"start\":\"23:00\",\"end\":\"07:00\"}}");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userSettingsRepository.findById(userId)).willReturn(Optional.of(settings));

        String result = userService.getNotificationPreferences(userId);

        assertThat(result).isEqualTo("{\"quietHours\":{\"start\":\"23:00\",\"end\":\"07:00\"}}");
    }

    @Test
    void updateNotificationPreferences_missingSettings_shouldCreateAndStoreSettings() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userSettingsRepository.findById(userId)).willReturn(Optional.empty());
        given(userSettingsRepository.save(any(UserSettings.class))).willAnswer(invocation -> invocation.getArgument(0));

        String result = userService.updateNotificationPreferences(
                userId,
                "{\"quietHours\":{\"start\":\"23:00\",\"end\":\"07:00\"}}");

        assertThat(result).isEqualTo("{\"quietHours\":{\"start\":\"23:00\",\"end\":\"07:00\"}}");
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    void changeMyPassword_validCurrentPassword_shouldUpdateHashAndRevokeSessions() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("Oldpass1!", "$2a$10$hash")).willReturn(true);
        given(passwordEncoder.encode("Newpass1!")).willReturn("$2a$new");

        userService.changeMyPassword(userId, "Oldpass1!", "Newpass1!");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$new");
        verify(eventPublisher).publishEvent(new UserSessionsRevocationRequested(userId));
    }

    @Test
    void changeMyPassword_invalidCurrentPassword_shouldThrowAndNotRevokeSessions() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong", "$2a$10$hash")).willReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> userService.changeMyPassword(userId, "wrong", "Newpass1!"))
                .isInstanceOf(UserSelfServiceException.class);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void resetPassword_shouldUpdateHashClearLockAndRevokeSessions() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.parse("2026-06-10T12:00:00+09:00");
        User user = user(userId);
        for (int count = 0; count < 5; count++) {
            user.recordFailedLogin(now);
        }
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.encode("Newpass1!")).willReturn("$2a$new");

        userService.resetPassword(userId, "Newpass1!");

        assertThat(user.getPasswordHash()).isEqualTo("$2a$new");
        assertThat(user.getFailedLoginCount()).isZero();
        assertThat(user.getLockedUntil()).isNull();
        verify(eventPublisher).publishEvent(new UserSessionsRevocationRequested(userId));
    }

    @Test
    void deleteMyAccount_shouldSoftDeleteAndRevokeSessions() {
        UUID userId = UUID.randomUUID();
        User user = user(userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        userService.deleteMyAccount(userId);

        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getEmail()).isEqualTo("deleted_" + userId + "@deleted.invalid");
        verify(eventPublisher).publishEvent(new UserSessionsRevocationRequested(userId));
    }

    private static User user(UUID userId) {
        User user = User.ofEmailPassword("user@example.com", "user", "$2a$10$hash", UUID.randomUUID());
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
