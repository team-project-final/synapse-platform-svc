package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.auth.service.OAuth2FailureHandler;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

class OAuth2FailureHandlerTest {

    @Test
    void onAuthenticationFailure_exceptionWithMessage_shouldRedirectToCallbackWithEncodedError() throws Exception {
        // Given
        OAuth2FailureHandler handler = new OAuth2FailureHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        BadCredentialsException exception = new BadCredentialsException("OAuth failed: invalid state");
        String encoded = URLEncoder.encode(exception.getMessage(), StandardCharsets.UTF_8);

        // When
        handler.onAuthenticationFailure(request, response, exception);

        // Then
        assertThat(response.getRedirectedUrl()).isEqualTo("/api/v1/auth/callback?error=" + encoded);
    }
}
