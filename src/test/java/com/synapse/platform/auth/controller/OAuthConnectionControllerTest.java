package com.synapse.platform.auth.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.auth.service.OAuthConnectionService;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OAuthConnectionControllerTest {

    private final OAuthConnectionService oauthConnectionService =
            org.mockito.Mockito.mock(OAuthConnectionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OAuthConnectionController(oauthConnectionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void list_shouldDelegateCurrentUser() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/api/v1/users/me/oauth")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isOk());

        verify(oauthConnectionService).listConnections(userId);
    }

    @Test
    void unlink_shouldDelegateProvider() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/users/me/oauth/{provider}", "google")
                        .principal(new TestingAuthenticationToken(userId.toString(), null)))
                .andExpect(status().isNoContent());

        verify(oauthConnectionService).unlink(userId, "google");
    }

    @Test
    void unlink_malformedPrincipal_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me/oauth/{provider}", "google")
                        .principal(new TestingAuthenticationToken("not-a-uuid", null)))
                .andExpect(status().isUnauthorized());
    }
}
