package com.synapse.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.jwt.JwtTokenProvider;
import com.synapse.platform.auth.jwt.RefreshTokenService;
import com.synapse.platform.auth.oauth.OAuth2SuccessHandler;
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
    void onAuthenticationSuccess_oauthUser_shouldRedirectToClientWithJwtTokens() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        OAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-123", "userId", userId.toString()),
                "sub");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null);
        JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
        RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
        given(jwtTokenProvider.createAccessToken(userId, List.of("ROLE_USER"))).willReturn("access-token");
        given(jwtTokenProvider.createRefreshToken(userId)).willReturn("refresh-token");
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler(
                jwtTokenProvider,
                refreshTokenService,
                "http://localhost:3000/auth/callback");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then
        assertThat(response.getRedirectedUrl())
                .startsWith("http://localhost:3000/auth/callback")
                .contains("access_token=access-token")
                .contains("refresh_token=refresh-token");
        verify(refreshTokenService).save(userId, "refresh-token");
    }
}
