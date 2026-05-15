package com.synapse.platform.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class JwtTokenProviderTest {

    private JwtProperties properties;
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        properties = testProperties();
        tokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void createAccessToken_validUser_shouldContainRequiredClaimsAndKid() {
        // Given
        UUID userId = UUID.randomUUID();

        // When
        String token = tokenProvider.createAccessToken(userId, List.of("ROLE_USER"));

        // Then
        Jws<Claims> parsed = parse(token);
        assertThat(parsed.getHeader().get("kid")).isEqualTo("test-kid");
        assertThat(parsed.getPayload().getSubject()).isEqualTo(userId.toString());
        assertThat(parsed.getPayload().getIssuer()).isEqualTo("synapse-auth");
        assertThat(parsed.getPayload().get("type", String.class)).isEqualTo("ACCESS");
        assertThat(parsed.getPayload().get("roles", List.class)).containsExactly("ROLE_USER");
        assertThat(parsed.getPayload().getExpiration()).isAfter(new Date());
    }

    @Test
    void createRefreshToken_validUser_shouldContainRefreshTypeWithoutRoles() {
        // Given
        UUID userId = UUID.randomUUID();

        // When
        String token = tokenProvider.createRefreshToken(userId);

        // Then
        Jws<Claims> parsed = parse(token);
        assertThat(parsed.getHeader().get("kid")).isEqualTo("test-kid");
        assertThat(parsed.getPayload().getSubject()).isEqualTo(userId.toString());
        assertThat(parsed.getPayload().get("type", String.class)).isEqualTo("REFRESH");
        assertThat(parsed.getPayload().get("roles")).isNull();
    }

    @Test
    void validateToken_expiredToken_shouldReturnFalse() {
        // Given
        String expiredToken = Jwts.builder()
                .header()
                .add("kid", properties.kid())
                .and()
                .subject(UUID.randomUUID().toString())
                .issuer(properties.issuer())
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .claim("type", "ACCESS")
                .signWith(properties.rsaPrivateKey(), Jwts.SIG.RS256)
                .compact();

        // When
        boolean valid = tokenProvider.validateToken(expiredToken);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void validateToken_tamperedToken_shouldReturnFalse() {
        // Given
        String token = tokenProvider.createRefreshToken(UUID.randomUUID());
        String tamperedToken = token.substring(0, token.length() - 2) + "aa";

        // When
        boolean valid = tokenProvider.validateToken(tamperedToken);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void validateToken_issuerMismatch_shouldReturnFalse() {
        // Given
        String token = Jwts.builder()
                .header()
                .add("kid", properties.kid())
                .and()
                .subject(UUID.randomUUID().toString())
                .issuer("other-issuer")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(60)))
                .claim("type", "ACCESS")
                .signWith(properties.rsaPrivateKey(), Jwts.SIG.RS256)
                .compact();

        // When
        boolean valid = tokenProvider.validateToken(token);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void validateAccessToken_accessToken_shouldReturnTrue() {
        // Given
        String token = tokenProvider.createAccessToken(UUID.randomUUID(), List.of("ROLE_USER"));

        // When
        boolean valid = tokenProvider.validateAccessToken(token);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void validateAccessToken_refreshToken_shouldReturnFalse() {
        // Given
        String token = tokenProvider.createRefreshToken(UUID.randomUUID());

        // When
        boolean valid = tokenProvider.validateAccessToken(token);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void validateRefreshToken_refreshToken_shouldReturnTrue() {
        // Given
        String token = tokenProvider.createRefreshToken(UUID.randomUUID());

        // When
        boolean valid = tokenProvider.validateRefreshToken(token);

        // Then
        assertThat(valid).isTrue();
    }

    @Test
    void validateRefreshToken_accessToken_shouldReturnFalse() {
        // Given
        String token = tokenProvider.createAccessToken(UUID.randomUUID(), List.of("ROLE_USER"));

        // When
        boolean valid = tokenProvider.validateRefreshToken(token);

        // Then
        assertThat(valid).isFalse();
    }

    @Test
    void getUserId_validToken_shouldReturnUserId() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.createRefreshToken(userId);

        // When
        UUID result = tokenProvider.getUserId(token);

        // Then
        assertThat(result).isEqualTo(userId);
    }

    @Test
    void getAuthentication_accessToken_shouldReturnUserIdPrincipalAndAuthorities() {
        // Given
        UUID userId = UUID.randomUUID();
        String token = tokenProvider.createAccessToken(userId, List.of("ROLE_USER", "ROLE_ADMIN"));

        // When
        Authentication authentication = tokenProvider.getAuthentication(token);

        // Then
        assertThat(authentication).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(authentication.getPrincipal()).isEqualTo(userId.toString());
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    private Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(properties.rsaPublicKey())
                .build()
                .parseSignedClaims(token);
    }

    private JwtProperties testProperties() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            return new JwtProperties(
                    Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                    Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()),
                    "test-kid",
                    "synapse-auth");
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
