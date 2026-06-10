package com.synapse.platform.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.service.JwtTokenProvider;
import com.synapse.platform.global.exception.GlobalExceptionHandler;
import com.synapse.platform.user.api.UserApi;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ERROR_CODE = "PLAT-002";
    private static final String SERVER_ERROR_CODE = "PLAT-999";
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final UserApi userApi;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            ObjectMapper objectMapper,
            UserApi userApi) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
        this.userApi = userApi;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication;
        try {
            if (!jwtTokenProvider.validateAccessToken(token)) {
                SecurityContextHolder.clearContext();
                writeUnauthorizedResponse(request, response);
                return;
            }
            UUID userId = jwtTokenProvider.getUserId(token);
            authentication = jwtTokenProvider.getAuthentication(token);
            if (!isLoginAllowed(userId, request, response)) {
                return;
            }
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            writeUnauthorizedResponse(request, response);
            return;
        }

        try {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }

    private void writeUnauthorizedResponse(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        writeProblemResponse(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                ERROR_CODE,
                "Invalid or expired token");
    }

    private boolean isLoginAllowed(
            UUID userId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        try {
            if (!userApi.isLoginAllowed(userId)) {
                SecurityContextHolder.clearContext();
                writeUnauthorizedResponse(request, response);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            log.error("Failed to verify login state for user {}", userId, exception);
            writeProblemResponse(
                    request,
                    response,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    SERVER_ERROR_CODE,
                    "Internal server error");
            return false;
        }
    }

    private void writeProblemResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String errorCode,
            String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new GlobalExceptionHandler.ErrorResponse(
                "https://api.synapse.app/errors/" + errorCode,
                status.getReasonPhrase(),
                status.value(),
                detail,
                errorCode,
                traceId(request)));
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute("traceId");
        return traceId == null ? UUID.randomUUID().toString() : String.valueOf(traceId);
    }
}
