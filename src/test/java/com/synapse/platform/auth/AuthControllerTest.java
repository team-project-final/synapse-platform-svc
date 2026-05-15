package com.synapse.platform.auth;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.jwt.JwtTokenProvider;
import com.synapse.platform.auth.jwt.RefreshTokenService;
import com.synapse.platform.shared.exception.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AuthControllerTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(jwtTokenProvider, refreshTokenService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void refresh_validRefreshToken_shouldReturnNewTokensAndRotateRefreshToken() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(jwtTokenProvider.validateRefreshToken("old-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("old-refresh-token")).willReturn(userId);
        given(refreshTokenService.isValid(userId, "old-refresh-token")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(userId, List.of("ROLE_USER"))).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(userId)).willReturn("new-refresh-token");

        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "old-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-token"));
        verify(refreshTokenService).rotate(userId, "new-refresh-token");
    }

    @Test
    void refresh_tamperedRefreshToken_shouldReturnUnauthorizedProblem() throws Exception {
        // Given
        given(jwtTokenProvider.validateRefreshToken("tampered-token")).willReturn(false);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "tampered-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void refresh_redisMismatch_shouldReturnUnauthorizedProblem() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(jwtTokenProvider.validateRefreshToken("old-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("old-refresh-token")).willReturn(userId);
        given(refreshTokenService.isValid(userId, "old-refresh-token")).willReturn(false);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "old-refresh-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void refresh_accessToken_shouldReturnUnauthorizedProblem() throws Exception {
        // Given
        given(jwtTokenProvider.validateRefreshToken("access-token")).willReturn(false);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", "access-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void refresh_blankRefreshToken_shouldReturnBadRequestProblem() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("PLAT-001"));
    }
}
