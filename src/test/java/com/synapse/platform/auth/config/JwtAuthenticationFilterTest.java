package com.synapse.platform.auth.config;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.service.JwtTokenProvider;
import com.synapse.platform.user.api.UserApi;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class JwtAuthenticationFilterTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final UserApi userApi = mock(UserApi.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .addFilters(new JwtAuthenticationFilter(jwtTokenProvider, new ObjectMapper(), userApi))
                .build();
    }

    @Test
    void doFilterInternal_validToken_shouldSetAuthenticationForProtectedEndpoint() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId.toString(), "access-token", List.of());
        given(jwtTokenProvider.validateAccessToken("access-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("access-token")).willReturn(userId);
        given(userApi.isLoginAllowed(userId)).willReturn(true);
        given(jwtTokenProvider.getAuthentication("access-token")).willReturn(authentication);

        // When & Then
        mockMvc.perform(get("/api/v1/protected")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(userId.toString()));
    }

    @Test
    void doFilterInternal_deletedOrSuspendedUser_shouldReturnUnauthorizedProblem() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(jwtTokenProvider.validateAccessToken("access-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("access-token")).willReturn(userId);
        given(jwtTokenProvider.getAuthentication("access-token"))
                .willReturn(new UsernamePasswordAuthenticationToken(userId.toString(), "access-token", List.of()));
        given(userApi.isLoginAllowed(userId)).willReturn(false);

        // When & Then
        mockMvc.perform(get("/api/v1/protected")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void doFilterInternal_loginStateLookupFails_shouldReturnServerErrorProblem() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(jwtTokenProvider.validateAccessToken("access-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("access-token")).willReturn(userId);
        given(jwtTokenProvider.getAuthentication("access-token"))
                .willReturn(new UsernamePasswordAuthenticationToken(userId.toString(), "access-token", List.of()));
        given(userApi.isLoginAllowed(userId)).willThrow(new IllegalStateException("database unavailable"));

        // When & Then
        mockMvc.perform(get("/api/v1/protected")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("PLAT-999"));
    }

    @Test
    void doFilterInternal_withoutTokenOnPermitAllPath_shouldPassThrough() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/auth/callback"))
                .andExpect(status().isOk())
                .andExpect(content().string("callback"));
    }

    @Test
    void doFilterInternal_withoutTokenOnProtectedPath_shouldLeaveUnauthenticated() throws Exception {
        // When & Then
        mockMvc.perform(get("/api/v1/protected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doFilterInternal_invalidToken_shouldReturnUnauthorizedProblem() throws Exception {
        // Given
        given(jwtTokenProvider.validateAccessToken("invalid-token")).willReturn(false);

        // When & Then
        mockMvc.perform(get("/api/v1/protected")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void doFilterInternal_refreshToken_shouldReturnUnauthorizedProblem() throws Exception {
        // Given
        given(jwtTokenProvider.validateAccessToken("refresh-token")).willReturn(false);

        // When & Then
        mockMvc.perform(get("/api/v1/protected")
                        .header("Authorization", "Bearer refresh-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void doFilterInternal_authenticationCreationFails_shouldReturnUnauthorizedProblem() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(jwtTokenProvider.validateAccessToken("broken-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("broken-token")).willReturn(userId);
        given(userApi.isLoginAllowed(userId)).willReturn(true);
        given(jwtTokenProvider.getAuthentication("broken-token"))
                .willThrow(new IllegalArgumentException("Invalid token claims"));

        // When & Then
        mockMvc.perform(get("/api/v1/protected")
                        .header("Authorization", "Bearer broken-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @RestController
    static class TestController {

        @GetMapping("/api/v1/auth/callback")
        String callback() {
            return "callback";
        }

        @GetMapping("/api/v1/protected")
        ResponseEntity<String> protectedEndpoint() {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                return ResponseEntity.status(401).build();
            }
            return ResponseEntity.ok(SecurityContextHolder.getContext().getAuthentication().getName());
        }
    }
}
