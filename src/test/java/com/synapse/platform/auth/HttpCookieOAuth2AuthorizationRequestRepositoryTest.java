package com.synapse.platform.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.config.HttpCookieOAuth2AuthorizationRequestRepository;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

class HttpCookieOAuth2AuthorizationRequestRepositoryTest {

    private final HttpCookieOAuth2AuthorizationRequestRepository repository =
            new HttpCookieOAuth2AuthorizationRequestRepository(new ObjectMapper());

    @Test
    void saveAuthorizationRequest_validRequest_shouldSetHttpOnlyCookie() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authRequest = authorizationRequest();

        // When
        repository.saveAuthorizationRequest(authRequest, request, response);

        // Then
        Cookie cookie = response.getCookie("oauth2_auth_request");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(180);
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(header -> assertThat(header).contains("SameSite=Lax"));
    }

    @Test
    void saveAuthorizationRequest_secureRequest_shouldSetSecureCookie() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSecure(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authRequest = authorizationRequest();

        // When
        repository.saveAuthorizationRequest(authRequest, request, response);

        // Then
        Cookie cookie = response.getCookie("oauth2_auth_request");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(response.getHeaders("Set-Cookie"))
                .anySatisfy(header -> assertThat(header).contains("Secure"));
    }

    @Test
    void loadAuthorizationRequest_savedCookie_shouldRestoreAuthorizationRequest() {
        // Given
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authRequest = authorizationRequest();
        repository.saveAuthorizationRequest(authRequest, saveRequest, saveResponse);

        MockHttpServletRequest loadRequest = new MockHttpServletRequest();
        loadRequest.setCookies(saveResponse.getCookie("oauth2_auth_request"));

        // When
        OAuth2AuthorizationRequest loaded = repository.loadAuthorizationRequest(loadRequest);

        // Then
        assertThat(loaded).isNotNull();
        assertThat(loaded.getAuthorizationUri()).isEqualTo(authRequest.getAuthorizationUri());
        assertThat(loaded.getClientId()).isEqualTo(authRequest.getClientId());
        assertThat(loaded.getRedirectUri()).isEqualTo(authRequest.getRedirectUri());
        assertThat(loaded.getState()).isEqualTo(authRequest.getState());
        assertThat(loaded.getScopes()).containsExactlyInAnyOrderElementsOf(authRequest.getScopes());
    }

    @Test
    void removeAuthorizationRequest_savedCookie_shouldReturnRequestAndDeleteCookie() {
        // Given
        MockHttpServletRequest saveRequest = new MockHttpServletRequest();
        MockHttpServletResponse saveResponse = new MockHttpServletResponse();
        OAuth2AuthorizationRequest authRequest = authorizationRequest();
        repository.saveAuthorizationRequest(authRequest, saveRequest, saveResponse);

        MockHttpServletRequest removeRequest = new MockHttpServletRequest();
        removeRequest.setCookies(saveResponse.getCookie("oauth2_auth_request"));
        MockHttpServletResponse removeResponse = new MockHttpServletResponse();

        // When
        OAuth2AuthorizationRequest removed = repository.removeAuthorizationRequest(removeRequest, removeResponse);

        // Then
        assertThat(removed).isNotNull();
        assertThat(removed.getState()).isEqualTo("state-123");
        Cookie deleted = removeResponse.getCookie("oauth2_auth_request");
        assertThat(deleted).isNotNull();
        assertThat(deleted.getMaxAge()).isZero();
    }

    @Test
    void saveAuthorizationRequest_nullRequest_shouldDeleteCookie() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        repository.saveAuthorizationRequest(null, request, response);

        // Then
        Cookie deleted = response.getCookie("oauth2_auth_request");
        assertThat(deleted).isNotNull();
        assertThat(deleted.getMaxAge()).isZero();
    }

    private OAuth2AuthorizationRequest authorizationRequest() {
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .clientId("google-client")
                .redirectUri("http://localhost/login/oauth2/code/google")
                .scopes(Set.of("openid", "email", "profile"))
                .state("state-123")
                .additionalParameters(Map.of("prompt", "select_account"))
                .build();
    }
}
