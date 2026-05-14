package com.synapse.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

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
    void onAuthenticationSuccess_oauthUser_shouldRedirectToCallbackWithUserId() throws Exception {
        // Given
        String userId = UUID.randomUUID().toString();
        OAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("sub", "google-123", "userId", userId),
                "sub");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(user, null);
        OAuth2SuccessHandler handler = new OAuth2SuccessHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        handler.onAuthenticationSuccess(request, response, authentication);

        // Then
        assertThat(response.getRedirectedUrl()).isEqualTo("/api/v1/auth/callback?userId=" + userId);
    }
}
