package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.event.UserEventPublisher;
import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.entity.TenantMember;
import com.synapse.platform.auth.exception.AccountDisabledException;
import com.synapse.platform.auth.exception.AccountLockedException;
import com.synapse.platform.auth.exception.EmailAlreadyExistsException;
import com.synapse.platform.auth.exception.InvalidEmailPasswordLoginException;
import com.synapse.platform.auth.exception.OAuthAccountPasswordLoginException;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.auth.util.SlugGenerator;
import com.synapse.platform.user.api.EmailPasswordUserCreateCommand;
import com.synapse.platform.user.api.LoginFailureResult;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserLoginCredential;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailPasswordAuthServiceTest {

    @Mock
    private UserApi userApi;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private SlugGenerator slugGenerator;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private UserEventPublisher userEventPublisher;

    @InjectMocks
    private EmailPasswordAuthService emailPasswordAuthService;

    @Test
    void signup_duplicateEmail_shouldThrowConflict() {
        given(userApi.existsByEmail("user@example.com")).willReturn(true);

        assertThatThrownBy(() -> emailPasswordAuthService.signup("user@example.com", "P@ssw0rd!"))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userApi, never()).createForEmailPassword(any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void signup_availableEmail_shouldCreateTenantUserAndOwnerMembership() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(userApi.existsByEmail("user@example.com")).willReturn(false);
        given(userApi.existsByUsername("user")).willReturn(false);
        given(passwordEncoder.encode("P@ssw0rd!")).willReturn("$2a$10$hash");
        given(slugGenerator.generate("user@example.com")).willReturn("user");
        given(tenantRepository.save(any(Tenant.class))).willAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            ReflectionTestUtils.setField(tenant, "id", tenantId);
            return tenant;
        });
        given(userApi.createForEmailPassword(any(EmailPasswordUserCreateCommand.class)))
                .willReturn(new UserInfo(userId, "user@example.com", "user", tenantId));

        SignupResult result = emailPasswordAuthService.signup("user@example.com", "P@ssw0rd!");

        ArgumentCaptor<EmailPasswordUserCreateCommand> commandCaptor =
                ArgumentCaptor.forClass(EmailPasswordUserCreateCommand.class);
        assertThat(result.userId()).isEqualTo(userId);
        verify(userApi).createForEmailPassword(commandCaptor.capture());
        assertThat(commandCaptor.getValue().email()).isEqualTo("user@example.com");
        assertThat(commandCaptor.getValue().username()).isEqualTo("user");
        assertThat(commandCaptor.getValue().passwordHash()).isEqualTo("$2a$10$hash");
        assertThat(commandCaptor.getValue().defaultTenantId()).isEqualTo(tenantId);
        verify(tenantMemberRepository).save(any(TenantMember.class));
        verify(userEventPublisher).publishUserRegistered(userId, "user@example.com", "user", tenantId);
    }

    @Test
    void signup_userCreateUniqueConflict_shouldThrowEmailAlreadyExistsException() {
        UUID tenantId = UUID.randomUUID();
        given(userApi.existsByEmail("user@example.com")).willReturn(false);
        given(userApi.existsByUsername("user")).willReturn(false);
        given(passwordEncoder.encode("P@ssw0rd!")).willReturn("$2a$10$hash");
        given(slugGenerator.generate("user@example.com")).willReturn("user");
        given(tenantRepository.save(any(Tenant.class))).willAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            ReflectionTestUtils.setField(tenant, "id", tenantId);
            return tenant;
        });
        given(userApi.createForEmailPassword(any(EmailPasswordUserCreateCommand.class)))
                .willThrow(new DataIntegrityViolationException("uq_users_email"));

        assertThatThrownBy(() -> emailPasswordAuthService.signup("user@example.com", "P@ssw0rd!"))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(tenantMemberRepository, never()).save(any());
    }

    @Test
    void login_missingEmail_shouldThrowGenericUnauthorizedWithoutFailureMutation() {
        given(userApi.findLoginCredentialByEmail("missing@example.com")).willReturn(Optional.empty());

        assertThatThrownBy(() -> emailPasswordAuthService.login("missing@example.com", "P@ssw0rd!"))
                .isInstanceOf(InvalidEmailPasswordLoginException.class);

        verify(userApi, never()).recordFailedLogin(any(), any());
        verify(refreshTokenService, never()).save(any(), any());
    }

    @Test
    void login_oAuthOnlyAccount_shouldThrowOAuthConflictMessage() {
        UUID userId = UUID.randomUUID();
        given(userApi.findLoginCredentialByEmail("user@example.com"))
                .willReturn(Optional.of(new UserLoginCredential(userId, "user@example.com", null, "active", 0, null)));

        assertThatThrownBy(() -> emailPasswordAuthService.login("user@example.com", "P@ssw0rd!"))
                .isInstanceOf(OAuthAccountPasswordLoginException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userApi, never()).recordFailedLogin(any(), any());
    }

    @Test
    void login_badPassword_shouldRecordFailureAndThrowUnauthorized() {
        UUID userId = UUID.randomUUID();
        given(userApi.findLoginCredentialByEmail("user@example.com"))
                .willReturn(Optional.of(new UserLoginCredential(
                        userId,
                        "user@example.com",
                        "$2a$10$hash",
                        "active",
                        0,
                        null)));
        given(passwordEncoder.matches("wrong-password", "$2a$10$hash")).willReturn(false);
        given(userApi.recordFailedLogin(any(), any())).willReturn(new LoginFailureResult(1, null));

        assertThatThrownBy(() -> emailPasswordAuthService.login("user@example.com", "wrong-password"))
                .isInstanceOf(InvalidEmailPasswordLoginException.class);

        verify(userApi).recordFailedLogin(any(), any());
        verify(jwtTokenProvider, never()).createAccessToken(any(), any());
    }

    @Test
    void login_fifthBadPassword_shouldRecordFailureAndThrowLocked() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime lockedUntil = OffsetDateTime.now().plusMinutes(15);
        given(userApi.findLoginCredentialByEmail("user@example.com"))
                .willReturn(Optional.of(new UserLoginCredential(
                        userId,
                        "user@example.com",
                        "$2a$10$hash",
                        "active",
                        4,
                        null)));
        given(passwordEncoder.matches("wrong-password", "$2a$10$hash")).willReturn(false);
        given(userApi.recordFailedLogin(any(), any())).willReturn(new LoginFailureResult(5, lockedUntil));

        assertThatThrownBy(() -> emailPasswordAuthService.login("user@example.com", "wrong-password"))
                .isInstanceOf(AccountLockedException.class);

        verify(userApi).recordFailedLogin(any(), any());
        verify(jwtTokenProvider, never()).createAccessToken(any(), any());
    }

    @Test
    void login_lockedAccount_shouldThrowLockedWithoutPasswordCheck() {
        UUID userId = UUID.randomUUID();
        OffsetDateTime lockedUntil = OffsetDateTime.now().plusMinutes(10);
        given(userApi.findLoginCredentialByEmail("user@example.com"))
                .willReturn(Optional.of(new UserLoginCredential(
                        userId,
                        "user@example.com",
                        "$2a$10$hash",
                        "active",
                        5,
                        lockedUntil)));

        assertThatThrownBy(() -> emailPasswordAuthService.login("user@example.com", "P@ssw0rd!"))
                .isInstanceOf(AccountLockedException.class);

        verify(passwordEncoder, never()).matches(any(), any());
        verify(userApi, never()).recordFailedLogin(any(), any());
    }

    @Test
    void login_validPassword_shouldResetFailuresIssueTokensAndSaveRefreshToken() {
        UUID userId = UUID.randomUUID();
        given(userApi.findLoginCredentialByEmail("user@example.com"))
                .willReturn(Optional.of(new UserLoginCredential(
                        userId,
                        "user@example.com",
                        "$2a$10$hash",
                        "active",
                        1,
                        null)));
        given(passwordEncoder.matches("P@ssw0rd!", "$2a$10$hash")).willReturn(true);
        given(userApi.findRoles(userId)).willReturn(List.of("ROLE_USER", "ROLE_ADMIN"));
        given(jwtTokenProvider.createAccessToken(userId, List.of("ROLE_USER", "ROLE_ADMIN")))
                .willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(userId)).willReturn("refresh-token");

        LoginResult result = emailPasswordAuthService.login("user@example.com", "P@ssw0rd!");

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(userApi).recordSuccessfulLogin(any(), any());
        verify(refreshTokenService).save(userId, "refresh-token");
    }

    @Test
    void login_suspendedAccount_shouldThrowDisabledWithoutPasswordCheck() {
        UUID userId = UUID.randomUUID();
        given(userApi.findLoginCredentialByEmail("user@example.com"))
                .willReturn(Optional.of(new UserLoginCredential(
                        userId,
                        "user@example.com",
                        "$2a$10$hash",
                        "suspended",
                        0,
                        null)));

        assertThatThrownBy(() -> emailPasswordAuthService.login("user@example.com", "P@ssw0rd!"))
                .isInstanceOf(AccountDisabledException.class)
                .hasMessageContaining("suspended");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtTokenProvider, never()).createAccessToken(any(), any());
    }
}
