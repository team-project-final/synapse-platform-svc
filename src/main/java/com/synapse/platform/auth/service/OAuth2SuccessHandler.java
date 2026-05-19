package com.synapse.platform.auth.service;

import com.synapse.platform.auth.AuthRoles;
import com.synapse.platform.auth.service.JwtTokenProvider;
import com.synapse.platform.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final String clientRedirectUri;

    public OAuth2SuccessHandler(
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            @Value("${app.oauth2.redirect-uri}") String clientRedirectUri) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.clientRedirectUri = clientRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        UUID userId = UUID.fromString(String.valueOf(oAuth2User.getAttributes().get("userId")));
        String accessToken = jwtTokenProvider.createAccessToken(userId, AuthRoles.DEFAULT_USER_ROLES);
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        String deviceFingerprint = request.getHeader("X-Device-Fingerprint");
        String ipAddress = request.getRemoteAddr();
        refreshTokenService.save(userId, refreshToken, deviceFingerprint, ipAddress);
        String redirectUrl = UriComponentsBuilder.fromUriString(clientRedirectUri)
                .queryParam("access_token", accessToken)
                .queryParam("refresh_token", refreshToken)
                .build()
                .toUriString();
        response.sendRedirect(redirectUrl);
    }
}
