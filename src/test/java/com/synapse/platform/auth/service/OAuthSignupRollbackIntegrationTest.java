package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

import com.synapse.platform.auth.entity.OAuthIdentity;
import com.synapse.platform.auth.repository.OAuthIdentityRepository;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.auth.util.SlugGenerator;
import com.synapse.platform.global.crypto.FieldEncryptor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
class OAuthSignupRollbackIntegrationTest {

    @Autowired
    @Qualifier("rollbackCustomOAuth2UserService")
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    @Qualifier("rollbackDelegate")
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> rollbackDelegate;

    @MockitoSpyBean
    private OAuthIdentityRepository oauthIdentityRepository;

    @Test
    void loadUser_oauthIdentitySaveFails_shouldRollbackSignupData() {
        // Given
        given(rollbackDelegate.loadUser(any())).willReturn(googleUser());
        doThrow(new IllegalStateException("identity save failed"))
                .when(oauthIdentityRepository)
                .save(any(OAuthIdentity.class));

        // When & Then
        assertThatThrownBy(() -> customOAuth2UserService.loadUser(userRequest("google")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("identity save failed");
        assertThat(userRepository.findByEmail("rollback@example.com")).isEmpty();
        assertThat(tenantRepository.existsBySlug("rollback")).isFalse();
    }

    private OAuth2User googleUser() {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "rollback-google-123",
                        "email", "rollback@example.com",
                        "name", "Rollback User",
                        "picture", ""),
                "sub");
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

    @TestConfiguration
    static class RollbackTestConfig {

        @Bean
        @Primary
        CustomOAuth2UserService rollbackCustomOAuth2UserService(
                UserRepository userRepository,
                UserApi userApi,
                OAuthIdentityRepository oauthIdentityRepository,
                TenantRepository tenantRepository,
                TenantMemberRepository tenantMemberRepository,
                SlugGenerator slugGenerator,
                FieldEncryptor fieldEncryptor,
                OAuth2UserService<OAuth2UserRequest, OAuth2User> rollbackDelegate) {
            return new CustomOAuth2UserService(
                    userApi,
                    oauthIdentityRepository,
                    tenantRepository,
                    tenantMemberRepository,
                    slugGenerator,
                    fieldEncryptor,
                    rollbackDelegate);
        }

        @Bean
        OAuth2UserService<OAuth2UserRequest, OAuth2User> rollbackDelegate() {
            return org.mockito.Mockito.mock(OAuth2UserService.class);
        }
    }
}
