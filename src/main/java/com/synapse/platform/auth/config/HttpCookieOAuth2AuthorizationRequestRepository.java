package com.synapse.platform.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public class HttpCookieOAuth2AuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    static final String COOKIE_NAME = "oauth2_auth_request";
    private static final int COOKIE_MAX_AGE = 180;

    private final ObjectMapper objectMapper;

    public HttpCookieOAuth2AuthorizationRequestRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return getCookieValue(request).map(this::deserialize).orElse(null);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorizationRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookie(response);
            return;
        }

        String encoded = serialize(authorizationRequest);
        Cookie cookie = new Cookie(COOKIE_NAME, encoded);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(isSecureRequest(request));
        cookie.setMaxAge(COOKIE_MAX_AGE);
        response.addCookie(cookie);
        String secureAttribute = isSecureRequest(request) ? "; Secure" : "";
        response.addHeader(
                "Set-Cookie",
                COOKIE_NAME + "=" + encoded + "; Path=/; HttpOnly; Max-Age="
                        + COOKIE_MAX_AGE + secureAttribute + "; SameSite=Lax");
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request,
            HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = loadAuthorizationRequest(request);
        deleteCookie(response);
        return authRequest;
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        try {
            String json = objectMapper.writeValueAsString(OAuth2AuthorizationRequestDto.from(request));
            return Base64.getUrlEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("OAuth2AuthorizationRequest serialization failed", e);
        }
    }

    private OAuth2AuthorizationRequest deserialize(String value) {
        try {
            String json = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, OAuth2AuthorizationRequestDto.class).toRequest();
        } catch (IOException e) {
            throw new IllegalStateException("OAuth2AuthorizationRequest deserialization failed", e);
        }
    }

    private Optional<String> getCookieValue(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        return request.isSecure() || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
    }

    private void deleteCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}