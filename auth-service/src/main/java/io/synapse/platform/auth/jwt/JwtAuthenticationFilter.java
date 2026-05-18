package io.synapse.platform.auth.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.synapse.platform.common.exception.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
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

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider jwtTokenProvider,
            ObjectMapper objectMapper) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
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
            authentication = jwtTokenProvider.getAuthentication(token);
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
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), new GlobalExceptionHandler.ErrorResponse(
                "https://api.synapse.app/errors/" + ERROR_CODE,
                status.getReasonPhrase(),
                status.value(),
                "Invalid or expired token",
                ERROR_CODE,
                traceId(request)));
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute("traceId");
        return traceId == null ? UUID.randomUUID().toString() : String.valueOf(traceId);
    }
}
