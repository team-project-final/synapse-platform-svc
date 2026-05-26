package com.synapse.platform.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.service.CustomOAuth2UserService;
import com.synapse.platform.auth.service.CustomOidcUserService;
import com.synapse.platform.auth.service.OAuth2FailureHandler;
import com.synapse.platform.auth.service.OAuth2SuccessHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {

    @Test
    void corsConfigurationSource_allowedOrigins_shouldAllowCredentialsForConfiguredOrigins() {
        // Given
        SecurityConfig config = securityConfig();
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/refresh");

        // When
        CorsConfiguration cors = config.corsConfigurationSource().getCorsConfiguration(request);

        // Then
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:3000", "http://localhost:5173");
        assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("*");
        assertThat(cors.getAllowCredentials()).isTrue();
    }

    @Test
    void passwordEncoder_shouldUseBCrypt() {
        // Given
        SecurityConfig config = securityConfig();

        // When & Then
        assertThat(config.passwordEncoder()).isInstanceOf(BCryptPasswordEncoder.class);
    }

    private SecurityConfig securityConfig() {
        return new SecurityConfig(
                mock(CustomOAuth2UserService.class),
                mock(CustomOidcUserService.class),
                mock(OAuth2SuccessHandler.class),
                mock(OAuth2FailureHandler.class),
                mock(JwtAuthenticationFilter.class),
                mock(ObjectMapper.class),
                List.of("http://localhost:3000", "http://localhost:5173"));
    }
}
