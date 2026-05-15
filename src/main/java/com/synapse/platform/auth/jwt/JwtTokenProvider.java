package com.synapse.platform.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
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

    private final JwtProperties properties;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
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
                .claim("roles", roles)
                .claim(TYPE_CLAIM, ACCESS_TOKEN_TYPE)
                .signWith(properties.rsaPrivateKey(), Jwts.SIG.RS256)
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
                .signWith(properties.rsaPrivateKey(), Jwts.SIG.RS256)
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
        Collection<? extends GrantedAuthority> authorities = authorities(claims.get("roles"));
        return new UsernamePasswordAuthenticationToken(userId, token, authorities);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(properties.rsaPublicKey())
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
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
