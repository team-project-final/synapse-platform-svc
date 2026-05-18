package com.synapse.platform.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CustomOidcUserServiceTest {

    @Mock
    private OAuthUserResolver oAuthUserResolver;

    @Mock
    private OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    @Test
    void loadUser_appleUser_shouldResolveUserAndReturnUserIdAttribute() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = User.ofOAuth("apple@example.com", "apple", "Apple User", null);
        ReflectionTestUtils.setField(user, "id", userId);
        given(delegate.loadUser(any())).willReturn(appleOidcUser());
        given(oAuthUserResolver.resolveUser(any(OAuthAttributes.class), any())).willReturn(user);
        CustomOidcUserService service = new CustomOidcUserService(oAuthUserResolver, delegate);

        // When
        OidcUser result = service.loadUser(oidcUserRequest());

        // Then
        ArgumentCaptor<OAuthAttributes> attributesCaptor = ArgumentCaptor.forClass(OAuthAttributes.class);
        verify(oAuthUserResolver).resolveUser(attributesCaptor.capture(), any());
        assertThat(attributesCaptor.getValue().provider()).isEqualTo("apple");
        assertThat(attributesCaptor.getValue().name()).isNull();
        assertThat(result.getAttributes()).containsEntry("userId", userId.toString());
        assertThat(result.getName()).isEqualTo("apple-123");
    }

    private OidcUser appleOidcUser() {
        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of(
                        "sub", "apple-123",
                        "email", "apple@example.com"));
        return new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken,
                "sub");
    }

    private OidcUserRequest oidcUserRequest() {
        ClientRegistration registration = ClientRegistration.withRegistrationId("apple")
                .clientId("apple-client")
                .clientSecret("apple-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/login/oauth2/code/apple")
                .authorizationUri("https://appleid.apple.com/auth/authorize")
                .tokenUri("https://appleid.apple.com/auth/token")
                .jwkSetUri("https://appleid.apple.com/auth/keys")
                .userNameAttributeName("sub")
                .clientName("apple")
                .scope("openid", "name", "email")
                .build();
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "token",
                Instant.now(),
                Instant.now().plusSeconds(60));
        OidcIdToken idToken = new OidcIdToken(
                "id-token",
                Instant.now(),
                Instant.now().plusSeconds(60),
                Map.of("sub", "apple-123"));
        return new OidcUserRequest(registration, accessToken, idToken);
    }
}
