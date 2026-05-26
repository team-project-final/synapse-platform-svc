package com.synapse.platform.auth;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.auth.controller.AuthController;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.service.JwtTokenProvider;
import com.synapse.platform.auth.service.RefreshTokenService;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AuthControllerTest {

    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(
                        jwtTokenProvider,
                        refreshTokenService,
                        "Lax",
                        false,
                        List.of("http://127.0.0.1:8088")))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void refresh_validRefreshCookie_shouldReturnAccessTokenAndRotateRefreshCookie() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(jwtTokenProvider.validateRefreshToken("old-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("old-refresh-token")).willReturn(userId);
        given(refreshTokenService.isValid(userId, "old-refresh-token")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(userId, List.of("ROLE_USER"))).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(userId)).willReturn("new-refresh-token");

        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://127.0.0.1:8088")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.allOf(
                        Matchers.containsString("refresh_token=new-refresh-token"),
                        Matchers.containsString("Path=/api/v1/auth"),
                        Matchers.containsString("HttpOnly"),
                        Matchers.containsString("SameSite=Lax"))))
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        verify(refreshTokenService).rotate(userId, "old-refresh-token", "new-refresh-token");
    }

    @Test
    void refresh_disallowedOrigin_shouldReturnForbiddenWithoutRotatingToken() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "https://evil.example")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(jwtTokenProvider, refreshTokenService);
    }

    @Test
    void refresh_missingOrigin_shouldReturnForbiddenWithoutRotatingToken() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        verifyNoInteractions(jwtTokenProvider, refreshTokenService);
    }

    @Test
    void refresh_tamperedRefreshCookie_shouldReturnUnauthorizedProblem() throws Exception {
        // Given
        given(jwtTokenProvider.validateRefreshToken("tampered-token")).willReturn(false);

        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://127.0.0.1:8088")
                        .cookie(new Cookie("refresh_token", "tampered-token")))
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
                        .header("Origin", "http://127.0.0.1:8088")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void refresh_tokenRotatedAfterValidation_shouldReturnUnauthorizedProblem() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        given(jwtTokenProvider.validateRefreshToken("old-refresh-token")).willReturn(true);
        given(jwtTokenProvider.getUserId("old-refresh-token")).willReturn(userId);
        given(refreshTokenService.isValid(userId, "old-refresh-token")).willReturn(true);
        given(jwtTokenProvider.createAccessToken(userId, List.of("ROLE_USER"))).willReturn("new-access-token");
        given(jwtTokenProvider.createRefreshToken(userId)).willReturn("new-refresh-token");
        doThrow(new UnauthorizedTokenException("Refresh token does not match stored token"))
                .when(refreshTokenService)
                .rotate(userId, "old-refresh-token", "new-refresh-token");

        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://127.0.0.1:8088")
                        .cookie(new Cookie("refresh_token", "old-refresh-token")))
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
                        .header("Origin", "http://127.0.0.1:8088")
                        .cookie(new Cookie("refresh_token", "access-token")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }

    @Test
    void refresh_missingRefreshCookie_shouldReturnUnauthorizedProblem() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header("Origin", "http://127.0.0.1:8088"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("PLAT-002"));
    }
}
