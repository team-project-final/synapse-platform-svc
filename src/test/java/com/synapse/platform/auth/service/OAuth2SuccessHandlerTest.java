package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.synapse.platform.user.api.UserApi;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

class OAuth2SuccessHandlerTest {

    @Test
    void onAuthenticationSuccess_oauthUser_shouldRedirectWithAccessTokenAndRefreshCookie() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        OAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-123", "userId", userId.toString()),
                "sub");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        UserApi userApi = mock(UserApi.class);
        given(userApi.findRoles(userId)).willReturn(List.of("ROLE_USER", "ROLE_ADMIN"));
        given(jwtTokenProvider.createAccessToken(userId, List.of("ROLE_USER", "ROLE_ADMIN")))
                .willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(userId)).willReturn("refresh-token");
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                jwtTokenProvider,
                refreshTokenService,
                userApi,
                "http://localhost:3000/auth/callback",
                "Lax",
                false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Device-Fingerprint", "device-1");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then
        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:3000/auth/callback")
                .contains("access_token=access-token")
                .doesNotContain("refresh_token");
        assertThat(response.getHeader("Set-Cookie"))
                .contains("refresh_token=refresh-token")
                .contains("Path=/api/v1/auth")
                .contains("HttpOnly")
                .contains("SameSite=Lax");
        verify(refreshTokenService).save(userId, "refresh-token", "device-1", "127.0.0.1");
    }

    @Test
    void onAuthenticationSuccess_newOAuthUser_shouldOnlyIssueTokensAndRedirect() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        OAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-123",
                        "userId", userId.toString(),
                        "isNewUser", true,
                        "synapseEmail", "new@example.com",
                        "synapseDisplayName", "New User",
                        "synapseTenantId", tenantId.toString()),
                "sub");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        UserApi userApi = mock(UserApi.class);
        given(userApi.findRoles(userId)).willReturn(List.of("ROLE_USER"));
        given(jwtTokenProvider.createAccessToken(userId, List.of("ROLE_USER"))).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(userId)).willReturn("refresh-token");
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                jwtTokenProvider,
                refreshTokenService,
                userApi,
                "http://localhost:3000/auth/callback",
                "Lax",
                false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then
        verify(refreshTokenService).save(userId, "refresh-token", null, "127.0.0.1");
        assertThat(response.getRedirectedUrl()).contains("access_token=access-token");
    }
}
