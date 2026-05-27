package com.synapse.platform.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.synapse.platform.auth.entity.RefreshToken;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.repository.RefreshTokenRepository;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
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
        assertThat(redisTemplate.opsForValue().get(refreshKey(userId, rawToken))).isEqualTo(tokenHash);
        assertThat(refreshTokenService.isValid(userId, rawToken)).isTrue();
    }

    @Test
    void save_sixthTokenEvictsOldestTokenAndKeepsFiveActiveSessions() {
        UUID userId = createUser().getId();

        refreshTokenService.save(userId, "token-1", null, null);
        refreshTokenService.save(userId, "token-2", null, null);
        refreshTokenService.save(userId, "token-3", null, null);
        refreshTokenService.save(userId, "token-4", null, null);
        refreshTokenService.save(userId, "token-5", null, null);
        refreshTokenService.save(userId, "token-6", null, null);

        assertThat(repository.countByUserId(userId)).isEqualTo(5);
        assertThat(refreshTokenService.isValid(userId, "token-1")).isFalse();
        assertThat(refreshTokenService.isValid(userId, "token-2")).isTrue();
        assertThat(refreshTokenService.isValid(userId, "token-3")).isTrue();
        assertThat(refreshTokenService.isValid(userId, "token-4")).isTrue();
        assertThat(refreshTokenService.isValid(userId, "token-5")).isTrue();
        assertThat(refreshTokenService.isValid(userId, "token-6")).isTrue();
        assertThat(redisTemplate.opsForValue().get(refreshKey(userId, "token-1"))).isNull();
    }

    @Test
    void isValid_cacheMissFallsBackToCurrentDbToken() {
        UUID userId = createUser().getId();
        String rawToken = "raw-token";

        refreshTokenService.save(userId, rawToken, null, null);
        redisTemplate.delete(refreshKey(userId, rawToken));

        assertThat(refreshTokenService.isValid(userId, rawToken)).isTrue();
    }

    @Test
    void rotate_replacesOnlyPresentedTokenAndPreservesMetadata() {
        UUID userId = createUser().getId();

        refreshTokenService.save(userId, "old-token", "device-fp", "127.0.0.1");
        refreshTokenService.save(userId, "other-token", "other-device", "10.0.0.1");
        refreshTokenService.rotate(userId, "old-token", "new-token");

        assertThat(repository.countByUserId(userId)).isEqualTo(2);
        assertThat(refreshTokenService.isValid(userId, "new-token")).isTrue();
        assertThat(refreshTokenService.isValid(userId, "old-token")).isFalse();
        assertThat(refreshTokenService.isValid(userId, "other-token")).isTrue();
        assertThat(repository.findByUserIdAndTokenHash(userId, RefreshToken.hash("new-token")))
                .get()
                .satisfies(token -> {
                    assertThat(token.getDeviceFingerprint()).isEqualTo("device-fp");
                    assertThat(token.getIpAddress()).isEqualTo("127.0.0.1");
                });
    }

    @Test
    void rotate_missingOldToken_shouldThrowUnauthorizedTokenException() {
        UUID userId = createUser().getId();

        assertThatThrownBy(() -> refreshTokenService.rotate(userId, "missing-token", "new-token"))
                .isInstanceOf(UnauthorizedTokenException.class);
    }

    @Test
    void save_concurrentSixthTokens_shouldKeepAtMostFiveActiveSessions() throws Exception {
        UUID userId = createUser().getId();
        CountDownLatch ready = new CountDownLatch(6);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(6);

        try {
            List<Future<Object>> futures = IntStream.rangeClosed(1, 6)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        refreshTokenService.save(userId, "concurrent-token-" + index, null, null);
                        return null;
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertThat(repository.countByUserId(userId)).isLessThanOrEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void delete_removesDbAndRedisToken() {
        UUID userId = createUser().getId();
        String rawToken = "raw-token";
        String tokenHash = RefreshToken.hash(rawToken);

        refreshTokenService.save(userId, rawToken, null, null);
        refreshTokenService.delete(userId);

        assertThat(repository.existsByTokenHash(tokenHash)).isFalse();
        assertThat(redisTemplate.opsForValue().get(refreshKey(userId, rawToken))).isNull();
        assertThat(refreshTokenService.isValid(userId, rawToken)).isFalse();
    }

    private static String postgresJdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(POSTGRES_PORT),
                POSTGRES_DATABASE);
    }

    private static String refreshKey(UUID userId, String rawToken) {
        return "refresh:" + userId + ":" + RefreshToken.hash(rawToken);
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
