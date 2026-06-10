package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.dto.OAuthAttributes;
import com.synapse.platform.auth.event.UserEventPublisher;
import com.synapse.platform.auth.entity.OAuthIdentity;
import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.entity.TenantMember;
import com.synapse.platform.auth.repository.OAuthIdentityRepository;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.user.api.EmailPasswordUserCreateCommand;
import com.synapse.platform.user.api.LoginFailureResult;
import com.synapse.platform.user.api.OAuthUserCreateCommand;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserLoginCredential;
import com.synapse.platform.user.api.UserSummary;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserSettings;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.user.repository.UserSettingsRepository;
import com.synapse.platform.auth.util.SlugGenerator;
import com.synapse.platform.global.crypto.FieldEncryptor;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OAuthUserResolverTest {

    private static final String AES_KEY = Base64.getEncoder()
            .encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));

    @Mock
    private UserRepository userRepository;

    @Mock
    private OAuthIdentityRepository oauthIdentityRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @Mock
    private SlugGenerator slugGenerator;

    @Mock
    private UserEventPublisher userEventPublisher;

    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(AES_KEY);

    @Test
    void resolveUser_newAppleUserWithNullName_shouldUseEmailPrefixDisplayName() {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OAuthAttributes attributes = new OAuthAttributes(
                "apple",
                "apple-123",
                "apple@example.com",
                null,
                null);
        given(oauthIdentityRepository.findByProviderAndProviderUserId("apple", "apple-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("apple@example.com")).willReturn(Optional.empty());
        given(slugGenerator.generate("apple@example.com")).willReturn("apple");
        given(tenantRepository.save(any(Tenant.class))).willAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            ReflectionTestUtils.setField(tenant, "id", tenantId);
            return tenant;
        });
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", userId);
            return user;
        });
        OAuthUserResolver resolver = resolver();

        // When
        OAuthResolvedUser result = resolver.resolveUser(attributes, "token");

        // Then
        ArgumentCaptor<Tenant> tenantCaptor = ArgumentCaptor.forClass(Tenant.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<OAuthIdentity> identityCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        assertThat(result.user().id()).isEqualTo(userId);
        assertThat(result.newUser()).isTrue();
        verify(tenantRepository).save(tenantCaptor.capture());
        verify(userRepository).save(userCaptor.capture());
        verify(oauthIdentityRepository).save(identityCaptor.capture());
        verify(tenantMemberRepository).save(any(TenantMember.class));
        verify(userSettingsRepository).save(any(UserSettings.class));
        verify(userEventPublisher).publishUserRegistered(userId, "apple@example.com", "apple", tenantId);
        assertThat(tenantCaptor.getValue().getName()).isEqualTo("apple");
        assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("apple");
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("apple@example.com");
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo("apple");
        assertThat(identityCaptor.getValue().getEmail()).isEqualTo("apple@example.com");
        assertThat(fieldEncryptor.decrypt(identityCaptor.getValue().getAccessTokenEnc())).isEqualTo("token");
    }

    @Test
    void resolveUser_existingIdentityWithDeletedUser_shouldThrowDisabledBeforeLoadingUser() {
        UUID userId = UUID.randomUUID();
        OAuthAttributes attributes = new OAuthAttributes(
                "google",
                "google-123",
                "deleted@example.com",
                "Deleted User",
                null);
        OAuthIdentity identity = OAuthIdentity.of(userId, "google", "google-123", "deleted@example.com");
        given(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-123"))
                .willReturn(Optional.of(identity));

        OAuthUserResolver resolver = new OAuthUserResolver(
                userApi(false),
                oauthIdentityRepository,
                tenantRepository,
                tenantMemberRepository,
                slugGenerator,
                fieldEncryptor,
                userEventPublisher);

        assertThatThrownBy(() -> resolver.resolveUser(attributes, "token"))
                .isInstanceOf(DisabledException.class);
    }

    private OAuthUserResolver resolver() {
        return new OAuthUserResolver(
                userApi(),
                oauthIdentityRepository,
                tenantRepository,
                tenantMemberRepository,
                slugGenerator,
                fieldEncryptor,
                userEventPublisher);
    }

    private UserApi userApi() {
        return userApi(true);
    }

    private UserApi userApi(boolean loginAllowed) {
        return new UserApi() {
            @Override
            public Optional<UserInfo> findById(UUID userId) {
                return userRepository.findById(userId).map(this::toUserInfo);
            }

            @Override
            public Optional<UserInfo> findByEmail(String email) {
                return userRepository.findByEmail(email).map(this::toUserInfo);
            }

            @Override
            public List<UserSummary> findSummariesByIds(Collection<UUID> userIds) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public Optional<UserLoginCredential> findLoginCredentialByEmail(String email) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public boolean isLoginAllowed(UUID userId) {
                return loginAllowed;
            }

            @Override
            public boolean existsByEmail(String email) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public boolean existsByUsername(String username) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public boolean hasPasswordLogin(UUID userId) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public List<String> findRoles(UUID userId) {
                return List.of("ROLE_USER");
            }

            @Override
            public String getNotificationPreferences(UUID userId) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public String updateNotificationPreferences(UUID userId, String notificationPreferences) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public UserInfo createForOAuth(OAuthUserCreateCommand command) {
                User user = User.ofOAuth(
                        command.email(),
                        command.slug(),
                        command.displayName(),
                        command.avatarUrl());
                user.updateDefaultTenantId(command.defaultTenantId());
                User saved = userRepository.save(user);
                userSettingsRepository.save(UserSettings.defaultFor(saved.getId()));
                return toUserInfo(saved);
            }

            @Override
            public UserInfo createForEmailPassword(EmailPasswordUserCreateCommand command) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public LoginFailureResult recordFailedLogin(UUID userId, OffsetDateTime now) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public void recordSuccessfulLogin(UUID userId, OffsetDateTime now) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            @Override
            public void resetPassword(UUID userId, String newPassword) {
                throw new UnsupportedOperationException("Not used by OAuth tests");
            }

            private UserInfo toUserInfo(User user) {
                return new UserInfo(user.getId(), user.getEmail(), user.getDisplayName(), user.getDefaultTenantId());
            }
        };
    }
}
