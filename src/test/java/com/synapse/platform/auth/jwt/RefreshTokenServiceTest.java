package com.synapse.platform.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenServiceTest {

    private static final long SEVEN_DAYS_SECONDS = 7 * 24 * 60 * 60;

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                redis.getHost(),
                redis.getMappedPort(6379));
        connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        refreshTokenService = new RefreshTokenService(redisTemplate);
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    void save_validRefreshToken_shouldStoreTokenWithSevenDayTtl() {
        // Given
        UUID userId = UUID.randomUUID();

        // When
        refreshTokenService.save(userId, "refresh-token");

        // Then
        assertThat(refreshTokenService.get(userId)).contains("refresh-token");
        Long ttl = redisTemplate.getExpire("refresh:" + userId, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(SEVEN_DAYS_SECONDS - 5, SEVEN_DAYS_SECONDS);
    }

    @Test
    void rotate_existingRefreshToken_shouldReplaceStoredToken() {
        // Given
        UUID userId = UUID.randomUUID();
        refreshTokenService.save(userId, "old-token");

        // When
        refreshTokenService.rotate(userId, "new-token");

        // Then
        assertThat(refreshTokenService.get(userId)).contains("new-token");
        assertThat(refreshTokenService.isValid(userId, "old-token")).isFalse();
        assertThat(refreshTokenService.isValid(userId, "new-token")).isTrue();
    }

    @Test
    void delete_existingRefreshToken_shouldRemoveToken() {
        // Given
        UUID userId = UUID.randomUUID();
        refreshTokenService.save(userId, "refresh-token");

        // When
        refreshTokenService.delete(userId);

        // Then
        assertThat(refreshTokenService.get(userId)).isEmpty();
    }

    @Test
    void get_missingRefreshToken_shouldReturnEmpty() {
        // Given
        UUID userId = UUID.randomUUID();

        // When
        Optional<String> result = refreshTokenService.get(userId);

        // Then
        assertThat(result).isEmpty();
    }
}
