package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.auth.entity.RefreshToken;
import com.synapse.platform.auth.repository.RefreshTokenRepository;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.repository.UserRepository;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.flyway.enabled=true")
class RefreshTokenServiceTest {

    private static final String POSTGRES_DATABASE = "testdb";
    private static final String POSTGRES_USERNAME = "test";
    private static final String POSTGRES_PASSWORD = "test";
    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;

    @Container
    static GenericContainer<?> postgres = new GenericContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withEnv("POSTGRES_DB", POSTGRES_DATABASE)
            .withEnv("POSTGRES_USER", POSTGRES_USERNAME)
            .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
            .withExposedPorts(POSTGRES_PORT)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7"))
            .withExposedPorts(REDIS_PORT)
            .waitingFor(Wait.forListeningPort());

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository repository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", RefreshTokenServiceTest::postgresJdbcUrl);
        registry.add("spring.datasource.username", () -> POSTGRES_USERNAME);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(REDIS_PORT));
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(postgresJdbcUrl(), POSTGRES_USERNAME, POSTGRES_PASSWORD)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @BeforeEach
    void cleanUp() {
        repository.deleteAll();
        userRepository.deleteAll();
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    @Test
    void save_storesHashInDbAndRedis() {
        UUID userId = createUser().getId();
        String rawToken = "raw-token";
        String tokenHash = RefreshToken.hash(rawToken);

        refreshTokenService.save(userId, rawToken, "device-fp", "127.0.0.1");

        assertThat(repository.existsByTokenHash(tokenHash)).isTrue();
        assertThat(repository.findAll())
                .singleElement()
                .satisfies(token -> {
                    assertThat(token.getUserId()).isEqualTo(userId);
                    assertThat(token.getTokenHash()).isEqualTo(tokenHash);
                    assertThat(token.getTokenHash()).isNotEqualTo(rawToken);
                    assertThat(token.getDeviceFingerprint()).isEqualTo("device-fp");
                    assertThat(token.getIpAddress()).isEqualTo("127.0.0.1");
                });
        assertThat(redisTemplate.opsForValue().get(refreshKey(userId))).isEqualTo(tokenHash);
        assertThat(refreshTokenService.isValid(userId, rawToken)).isTrue();
    }

    @Test
    void save_secondTokenInvalidatesOldToken() {
        UUID userId = createUser().getId();

        refreshTokenService.save(userId, "old-token", null, null);
        refreshTokenService.save(userId, "new-token", null, null);

        assertThat(repository.countByUserId(userId)).isOne();
        assertThat(refreshTokenService.isValid(userId, "new-token")).isTrue();
        assertThat(refreshTokenService.isValid(userId, "old-token")).isFalse();
    }

    @Test
    void isValid_cacheMissFallsBackToCurrentDbToken() {
        UUID userId = createUser().getId();
        String rawToken = "raw-token";

        refreshTokenService.save(userId, rawToken, null, null);
        redisTemplate.delete(refreshKey(userId));

        assertThat(refreshTokenService.isValid(userId, rawToken)).isTrue();
    }

    @Test
    void rotate_replacesOldToken() {
        UUID userId = createUser().getId();

        refreshTokenService.save(userId, "old-token", null, null);
        refreshTokenService.rotate(userId, "new-token");

        assertThat(repository.countByUserId(userId)).isOne();
        assertThat(refreshTokenService.isValid(userId, "new-token")).isTrue();
        assertThat(refreshTokenService.isValid(userId, "old-token")).isFalse();
    }

    @Test
    void delete_removesDbAndRedisToken() {
        UUID userId = createUser().getId();
        String rawToken = "raw-token";
        String tokenHash = RefreshToken.hash(rawToken);

        refreshTokenService.save(userId, rawToken, null, null);
        refreshTokenService.delete(userId);

        assertThat(repository.existsByTokenHash(tokenHash)).isFalse();
        assertThat(redisTemplate.opsForValue().get(refreshKey(userId))).isNull();
        assertThat(refreshTokenService.isValid(userId, rawToken)).isFalse();
    }

    private static String postgresJdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(POSTGRES_PORT),
                POSTGRES_DATABASE);
    }

    private static String refreshKey(UUID userId) {
        return "refresh:" + userId;
    }

    private User createUser() {
        String suffix = UUID.randomUUID().toString();
        return userRepository.save(User.ofOAuth(
                "refresh-" + suffix + "@example.com",
                "refresh-" + suffix,
                "Refresh User",
                "https://example.com/avatar.png"));
    }
}
