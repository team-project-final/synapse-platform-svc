package io.synapse.platform.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OAuth2LoginIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void callback_authenticatedOAuthUser_shouldReturnOk() throws Exception {
        // Given
        String userId = UUID.randomUUID().toString();
        OAuth2User user = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of(
                        "sub", "google-123",
                        "email", "user@example.com",
                        "name", "Test User",
                        "picture", "",
                        "userId", userId),
                "sub");

        // When & Then
        mockMvc.perform(get("/api/v1/auth/callback")
                        .param("userId", userId)
                        .with(oauth2Login().oauth2User(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    void callback_missingUserId_shouldReturnBadRequest() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/auth/callback"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protectedEndpoint_withoutAuthentication_shouldReturnRedirect() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/private"))
                .andExpect(status().is3xxRedirection());
    }
}
