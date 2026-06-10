package com.synapse.platform.auth.service;

import com.synapse.platform.user.api.UserApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserApi userApi;
    private final String clientRedirectUri;
    private final String sameSite;
    private final boolean secure;

    public OAuth2SuccessHandler(
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            UserApi userApi,
            @Value("${app.oauth2.redirect-uri}") String clientRedirectUri,
            @Value("${app.cookie.same-site}") String sameSite,
            @Value("${app.cookie.secure}") boolean secure) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.userApi = userApi;
        this.clientRedirectUri = clientRedirectUri;
        this.sameSite = sameSite;
        this.secure = secure;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        UUID userId = UUID.fromString(String.valueOf(oAuth2User.getAttributes().get("userId")));
        String accessToken = jwtTokenProvider.createAccessToken(userId, userApi.findRoles(userId));
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        String deviceFingerprint = request.getHeader("X-Device-Fingerprint");
        String ipAddress = request.getRemoteAddr();
        refreshTokenService.save(userId, refreshToken, deviceFingerprint, ipAddress);
        ResponseCookie refreshCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .path("/api/v1/auth")
                .maxAge(Duration.ofDays(7))
                .sameSite(sameSite)
                .secure(secure)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
        String redirectUrl = UriComponentsBuilder.fromUriString(clientRedirectUri)
                .queryParam("access_token", accessToken)
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
