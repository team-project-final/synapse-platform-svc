package com.synapse.platform.auth;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.synapse.platform.auth.config.CorsConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

class CorsConfigTest {

    @Test
    void addCorsMappings_configuredAllowedOrigins_shouldRegisterWhitelistCorsPolicy() {
        // Given
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", List.of("http://localhost:3000"));
        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        given(registry.addMapping("/api/**")).willReturn(registration);
        given(registration.allowedOrigins("http://localhost:3000")).willReturn(registration);
        given(registration.allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH")).willReturn(registration);
        given(registration.allowedHeaders("Authorization", "Content-Type")).willReturn(registration);
        given(registration.allowCredentials(true)).willReturn(registration);
        given(registration.maxAge(3600)).willReturn(registration);

        // When
        corsConfig.addCorsMappings(registry);

        // Then
        verify(registry).addMapping("/api/**");
        verify(registration).allowedOrigins("http://localhost:3000");
        verify(registration).allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH");
        verify(registration).allowedHeaders("Authorization", "Content-Type");
        verify(registration).allowCredentials(true);
        verify(registration).maxAge(3600);
    }
}
