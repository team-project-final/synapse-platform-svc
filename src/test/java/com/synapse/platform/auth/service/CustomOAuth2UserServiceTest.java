package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.entity.OAuthIdentity;
import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.entity.TenantMember;
import com.synapse.platform.auth.event.UserEventPublisher;
import com.synapse.platform.auth.repository.OAuthIdentityRepository;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.user.api.EmailPasswordUserCreateCommand;
import com.synapse.platform.user.api.LoginFailureResult;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserSettings;
import com.synapse.platform.user.api.OAuthUserCreateCommand;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserLoginCredential;
import com.synapse.platform.user.api.UserSummary;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.user.repository.UserSettingsRepository;
import com.synapse.platform.auth.util.SlugGenerator;
import com.synapse.platform.global.crypto.FieldEncryptor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

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

    @Mock
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    private final FieldEncryptor fieldEncryptor = new FieldEncryptor(AES_KEY);

    @Test
    void loadUser_existingIdentity_shouldReturnExistingUserId() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = userWithId(userId, "user@example.com", "user");
        OAuthIdentity identity = OAuthIdentity.of(userId, "google", "google-123", "user@example.com");
        given(delegate.loadUser(any())).willReturn(googleUser());
        given(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-123"))
                .willReturn(Optional.of(identity));
        given(userRepository.findById(userId)).willReturn(Optional.of(existingUser));
        CustomOAuth2UserService service = service();

        // When
        OAuth2User result = service.loadUser(userRequest("google"));

        // Then
        assertThat(result.getAttributes()).containsEntry("userId", userId.toString());
        assertThat(result.getAttributes()).containsEntry("isNewUser", false);
        verify(userRepository, never()).findByEmail(any());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void loadUser_existingEmailWithoutIdentity_shouldCreateIdentityOnly() {
        // Given
        UUID userId = UUID.randomUUID();
        User existingUser = userWithId(userId, "user@example.com", "user");
        given(delegate.loadUser(any())).willReturn(googleUser());
        given(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.of(existingUser));
        CustomOAuth2UserService service = service();

        // When
        OAuth2User result = service.loadUser(userRequest("google"));

        // Then
        ArgumentCaptor<OAuthIdentity> identityCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        assertThat(result.getAttributes()).containsEntry("userId", userId.toString());
        assertThat(result.getAttributes()).containsEntry("isNewUser", false);
        verify(oauthIdentityRepository).save(identityCaptor.capture());
        assertThat(fieldEncryptor.decrypt(identityCaptor.getValue().getAccessTokenEnc())).isEqualTo("token");
        verify(tenantRepository, never()).save(any());
        verify(tenantMemberRepository, never()).save(any());
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    void loadUser_newGoogleUser_shouldCreateTenantUserIdentityMemberSettings() {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(delegate.loadUser(any())).willReturn(googleUser());
        given(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.empty());
        given(slugGenerator.generate("user@example.com")).willReturn("user");
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
        CustomOAuth2UserService service = service();

        // When
        OAuth2User result = service.loadUser(userRequest("google"));

        // Then
        ArgumentCaptor<OAuthIdentity> identityCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        assertThat(result.getAttributes()).containsEntry("userId", userId.toString());
        assertThat(result.getAttributes()).containsEntry("isNewUser", true);
        assertThat(result.getAttributes()).containsEntry("synapseEmail", "user@example.com");
        assertThat(result.getAttributes()).containsEntry("synapseDisplayName", "Test User");
        assertThat(result.getAttributes()).containsEntry("synapseTenantId", tenantId.toString());
        verify(tenantRepository).save(any(Tenant.class));
        verify(userRepository).save(any(User.class));
        verify(oauthIdentityRepository).save(identityCaptor.capture());
        assertThat(fieldEncryptor.decrypt(identityCaptor.getValue().getAccessTokenEnc())).isEqualTo("token");
        verify(tenantMemberRepository).save(any(TenantMember.class));
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    void loadUser_newGithubUserWithNullEmail_shouldUsePlaceholderEmailAndKeepIdentityEmailNull() {
        // Given
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        given(delegate.loadUser(any())).willReturn(githubUserWithoutEmail());
        given(oauthIdentityRepository.findByProviderAndProviderUserId("github", "12345"))
                .willReturn(Optional.empty());
        given(slugGenerator.generate("octocat@github.placeholder")).willReturn("octocat");
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
        CustomOAuth2UserService service = service();

        // When
        OAuth2User result = service.loadUser(userRequest("github"));

        // Then
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<OAuthIdentity> identityCaptor = ArgumentCaptor.forClass(OAuthIdentity.class);
        assertThat(result.getAttributes()).containsEntry("userId", userId.toString());
        assertThat(result.getAttributes()).containsEntry("isNewUser", true);
        assertThat(result.getAttributes()).containsEntry("synapseEmail", "octocat@github.placeholder");
        assertThat(result.getAttributes()).containsEntry("synapseDisplayName", "octocat");
        assertThat(result.getAttributes()).containsEntry("synapseTenantId", tenantId.toString());
        verify(userRepository).save(userCaptor.capture());
        verify(oauthIdentityRepository).save(identityCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("octocat@github.placeholder");
        assertThat(identityCaptor.getValue().getEmail()).isNull();
    }

    @Test
    void loadUser_repositoryFailure_shouldRollbackTransactionBoundary() {
        // Given
        UUID tenantId = UUID.randomUUID();
        given(delegate.loadUser(any())).willReturn(googleUser());
        given(oauthIdentityRepository.findByProviderAndProviderUserId("google", "google-123"))
                .willReturn(Optional.empty());
        given(userRepository.findByEmail("user@example.com")).willReturn(Optional.empty());
        given(slugGenerator.generate("user@example.com")).willReturn("user");
        given(tenantRepository.save(any(Tenant.class))).willAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            ReflectionTestUtils.setField(tenant, "id", tenantId);
            return tenant;
        });
        given(userRepository.save(any(User.class))).willThrow(new IllegalStateException("save failed"));
        CustomOAuth2UserService service = service();

        // When & Then
        assertThatThrownBy(() -> service.loadUser(userRequest("google")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("save failed");
        verify(oauthIdentityRepository, never()).save(any());
        verify(tenantMemberRepository, never()).save(any());
        verify(userSettingsRepository, never()).save(any());
    }

    private CustomOAuth2UserService service() {
        return new CustomOAuth2UserService(
                userApi(),
                oauthIdentityRepository,
                tenantRepository,
                tenantMemberRepository,
                slugGenerator,
                fieldEncryptor,
                userEventPublisher,
                delegate);
    }

    private UserApi userApi() {
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
                return true;
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

    private OAuth2User googleUser() {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-123",
                        "email", "user@example.com",
                        "name", "Test User",
                        "picture", "https://example.com/avatar.png"),
                "sub");
    }

    private OAuth2User githubUserWithoutEmail() {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "id", 12345,
                        "login", "octocat",
                        "avatar_url", "https://example.com/octocat.png"),
                "id");
    }

    private OAuth2UserRequest userRequest(String registrationId) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(registrationId)
                .clientId(registrationId + "-client")
                .clientSecret(registrationId + "-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/" + registrationId)
                .authorizationUri("https://example.com/oauth/authorize")
                .tokenUri("https://example.com/oauth/token")
                .userInfoUri("https://example.com/userinfo")
                .userNameAttributeName("sub")
                .clientName(registrationId)
                .scope("email", "profile")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60));
        return new OAuth2UserRequest(registration, accessToken);
    }

    private User userWithId(UUID userId, String email, String username) {
        User user = User.ofOAuth(email, username, "Test User", "https://example.com/avatar.png");
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }
}
