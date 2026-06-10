package com.synapse.platform.auth.service;

import com.synapse.platform.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final long ACCESS_TOKEN_TTL_SECONDS = 15 * 60;
    private static final long REFRESH_TOKEN_TTL_SECONDS = 7 * 24 * 60 * 60;
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String REFRESH_TOKEN_TYPE = "REFRESH";
    private static final String TYPE_CLAIM = "type";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProperties properties;
    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        // 키를 기동 시 1회 파싱 — 잘못된/누락 키면 빈 생성에서 즉시 실패(fail-fast).
        // 매 토큰 연산마다 재파싱하던 것을 제거(성능) + 런타임 500 대신 부팅 단계에서 발각.
        this.privateKey = properties.rsaPrivateKey();
        this.publicKey = properties.rsaPublicKey();
    }

    public String createAccessToken(UUID userId, List<String> roles) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header()
                .add("kid", properties.kid())
                .and()
                .subject(userId.toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ACCESS_TOKEN_TTL_SECONDS)))
                .claim(ROLES_CLAIM, tokenRoles(roles))
                .claim(TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String createRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header()
                .add("kid", properties.kid())
                .and()
                .subject(userId.toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(REFRESH_TOKEN_TTL_SECONDS)))
                .claim(TYPE_CLAIM, REFRESH_TOKEN_TYPE)
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    public boolean validateAccessToken(String token) {
        return validateTokenType(token, ACCESS_TOKEN_TYPE);
    }

    public boolean validateRefreshToken(String token) {
        return validateTokenType(token, REFRESH_TOKEN_TYPE);
    }

    public UUID getUserId(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public Authentication getAuthentication(String token) {
        Claims claims = parseClaims(token);
        String userId = claims.getSubject();
        Collection<? extends GrantedAuthority> authorities = authorities(claims.get(ROLES_CLAIM));
        return new UsernamePasswordAuthenticationToken(userId, token, authorities);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean validateTokenType(String token, String expectedType) {
        try {
            return expectedType.equals(parseClaims(token).get(TYPE_CLAIM, String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    private List<SimpleGrantedAuthority> authorities(Object rolesClaim) {
        if (!(rolesClaim instanceof List<?> roles)) {
            return List.of();
        }
        LinkedHashSet<String> authorities = new LinkedHashSet<>();
        for (Object role : roles) {
            if (role instanceof String value && !value.isBlank()) {
                authorities.add(springAuthority(value));
            }
        }
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    private List<String> tokenRoles(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        LinkedHashSet<String> tokenRoles = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            String normalized = role.trim();
            tokenRoles.add(normalized);
            if ("ROLE_ADMIN".equals(normalized)) {
                tokenRoles.add("ADMIN");
            }
        }
        return List.copyOf(tokenRoles);
    }

    private String springAuthority(String role) {
        String normalized = role.trim();
        if (normalized.startsWith(ROLE_PREFIX)) {
            return normalized;
        }
        String upperRole = normalized.toUpperCase(Locale.ROOT);
        if ("USER".equals(upperRole) || "ADMIN".equals(upperRole)) {
            return ROLE_PREFIX + upperRole;
        }
        return normalized;
    }
}
