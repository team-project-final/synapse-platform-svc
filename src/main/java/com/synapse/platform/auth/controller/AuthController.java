package com.synapse.platform.auth.controller;

import com.synapse.platform.auth.dto.EmailPasswordAuthRequest;
import com.synapse.platform.auth.dto.LoginResponse;
import com.synapse.platform.auth.dto.SignupResponse;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.service.EmailPasswordAuthService;
import com.synapse.platform.auth.service.JwtTokenProvider;
import com.synapse.platform.auth.service.LoginResult;
import com.synapse.platform.auth.service.RefreshTokenService;
import com.synapse.platform.auth.service.SignupResult;
import com.synapse.platform.user.api.UserApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final EmailPasswordAuthService emailPasswordAuthService;
    private final UserApi userApi;
    private final String sameSite;
    private final boolean secure;
    private final List<String> allowedOrigins;

    public AuthController(
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            EmailPasswordAuthService emailPasswordAuthService,
            UserApi userApi,
            @Value("${app.cookie.same-site}") String sameSite,
            @Value("${app.cookie.secure}") boolean secure,
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.emailPasswordAuthService = emailPasswordAuthService;
        this.userApi = userApi;
        this.sameSite = sameSite;
        this.secure = secure;
        this.allowedOrigins = allowedOrigins;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody EmailPasswordAuthRequest request) {
        SignupResult result = emailPasswordAuthService.signup(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new SignupResponse(result.userId()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody EmailPasswordAuthRequest request,
            HttpServletResponse response) {
        LoginResult result = emailPasswordAuthService.login(request.email(), request.password());
        addRefreshCookie(response, result.refreshToken());
        return ResponseEntity.ok(new LoginResponse(result.accessToken()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenRefreshResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response) {
        validateRefreshOrigin(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new UnauthorizedTokenException("Refresh token cookie missing");
        }
        if (!jwtTokenProvider.validateRefreshToken(refreshToken)) {
            throw new UnauthorizedTokenException("Invalid refresh token");
        }

        UUID userId = jwtTokenProvider.getUserId(refreshToken);
        if (!refreshTokenService.isValid(userId, refreshToken)) {
            throw new UnauthorizedTokenException("Refresh token does not match stored token");
        }
        if (!userApi.isLoginAllowed(userId)) {
            throw new UnauthorizedTokenException("User login is not allowed");
        }

        String newAccessToken = jwtTokenProvider.createAccessToken(userId, userApi.findRoles(userId));
        String newRefreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenService.rotate(userId, refreshToken, newRefreshToken);
        addRefreshCookie(response, newRefreshToken);
        return ResponseEntity.ok(new TokenRefreshResponse(newAccessToken));
    }

    private void addRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .sameSite(sameSite)
                .secure(secure)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void validateRefreshOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin == null || !allowedOrigins.contains(origin)) {
            throw new AccessDeniedException("Refresh origin is not allowed");
        }
    }

    public record TokenRefreshResponse(
            String accessToken
    ) {
    }
}
