package com.synapse.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.notification.dto.request.DeviceTokenRequest;
import com.synapse.platform.notification.entity.DeviceToken;
import com.synapse.platform.notification.entity.Platform;
import com.synapse.platform.notification.repository.DeviceTokenRepository;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.repository.UserRepository;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = "spring.flyway.enabled=true")
class DeviceTokenIntegrationTest {

    private static final String POSTGRES_DATABASE = "testdb";
    private static final String POSTGRES_USERNAME = "test";
    private static final String POSTGRES_PASSWORD = "test";
    private static final int POSTGRES_PORT = 5432;

    @Container
    static GenericContainer<?> postgres = new GenericContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16"))
            .withEnv("POSTGRES_DB", POSTGRES_DATABASE)
            .withEnv("POSTGRES_USER", POSTGRES_USERNAME)
            .withEnv("POSTGRES_PASSWORD", POSTGRES_PASSWORD)
            .withExposedPorts(POSTGRES_PORT)
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 2));

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DeviceTokenIntegrationTest::postgresJdbcUrl);
        registry.add("spring.datasource.username", () -> POSTGRES_USERNAME);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
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
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        deviceTokenRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void register_newDevice_shouldReturn201() throws Exception {
        UserFixture fixture = createUserFixture("new-device");

        mockMvc.perform(post("/api/v1/notifications/devices")
                        .with(user(fixture.userId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeviceTokenRequest("fcm-token-new", Platform.ANDROID))))
                .andExpect(status().isCreated());

        assertThat(deviceTokenRepository.findByToken("fcm-token-new"))
                .get()
                .satisfies(token -> {
                    assertThat(token.getTenantId()).isEqualTo(fixture.tenantId());
                    assertThat(token.getUserId()).isEqualTo(fixture.userId());
                    assertThat(token.getPlatform()).isEqualTo(Platform.ANDROID);
                    assertThat(token.isActive()).isTrue();
                });
    }

    @Test
    void register_sameTokenForDifferentUser_shouldKeepOneRowAndMoveOwner() throws Exception {
        UserFixture first = createUserFixture("first-owner");
        UserFixture second = createUserFixture("second-owner");

        register(first.userId(), "shared-token", Platform.IOS);
        register(second.userId(), "shared-token", Platform.WEB);

        assertThat(deviceTokenRepository.findAll()).hasSize(1);
        assertThat(deviceTokenRepository.findByToken("shared-token"))
                .get()
                .satisfies(token -> {
                    assertThat(token.getTenantId()).isEqualTo(second.tenantId());
                    assertThat(token.getUserId()).isEqualTo(second.userId());
                    assertThat(token.getPlatform()).isEqualTo(Platform.IOS);
                });
    }

    @Test
    void register_existingToken_shouldSkipLimitCheckAndUpsert() throws Exception {
        UserFixture first = createUserFixture("existing-first");
        UserFixture second = createUserFixture("existing-second");
        register(first.userId(), "existing-token", Platform.ANDROID);
        for (int index = 0; index < 5; index++) {
            register(second.userId(), "limit-token-" + index, Platform.WEB);
        }

        register(second.userId(), "existing-token", Platform.IOS);

        assertThat(deviceTokenRepository.findAll()).hasSize(6);
        assertThat(deviceTokenRepository.findByToken("existing-token"))
                .get()
                .extracting(DeviceToken::getUserId)
                .isEqualTo(second.userId());
    }

    @Test
    void register_sixthNewDevice_shouldReturn409() throws Exception {
        UserFixture fixture = createUserFixture("limit-user");
        for (int index = 0; index < 5; index++) {
            register(fixture.userId(), "user-token-" + index, Platform.ANDROID);
        }

        mockMvc.perform(post("/api/v1/notifications/devices")
                        .with(user(fixture.userId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeviceTokenRequest("sixth-token", Platform.ANDROID))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAT-NOTIFICATION-001"));
    }

    @Test
    void register_invalidPlatform_shouldReturn400() throws Exception {
        UserFixture fixture = createUserFixture("invalid-platform");

        mockMvc.perform(post("/api/v1/notifications/devices")
                        .with(user(fixture.userId().toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "invalid-platform-token",
                                  "platform": "MOBILE"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withoutJwt_shouldReturn401() throws Exception {
        mockMvc.perform(post("/api/v1/notifications/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new DeviceTokenRequest("no-jwt-token", Platform.ANDROID))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unregister_ownDevice_shouldReturn204() throws Exception {
        UserFixture fixture = createUserFixture("delete-owner");
        register(fixture.userId(), "delete-token", Platform.ANDROID);
        UUID deviceId = deviceTokenRepository.findByToken("delete-token").orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/notifications/devices/{id}", deviceId)
                        .with(user(fixture.userId().toString())))
                .andExpect(status().isNoContent());

        assertThat(deviceTokenRepository.findById(deviceId)).isEmpty();
    }

    @Test
    void unregister_otherUsersDevice_shouldReturn403() throws Exception {
        UserFixture owner = createUserFixture("delete-other-owner");
        UserFixture requester = createUserFixture("delete-other-requester");
        register(owner.userId(), "other-owner-token", Platform.ANDROID);
        UUID deviceId = deviceTokenRepository.findByToken("other-owner-token").orElseThrow().getId();

        mockMvc.perform(delete("/api/v1/notifications/devices/{id}", deviceId)
                        .with(user(requester.userId().toString())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PLAT-403"));
    }

    @Test
    void unregister_missingDevice_shouldReturn404() throws Exception {
        UserFixture fixture = createUserFixture("delete-missing");

        mockMvc.perform(delete("/api/v1/notifications/devices/{id}", UUID.randomUUID())
                        .with(user(fixture.userId().toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PLAT-404"));
    }

    private void register(UUID userId, String token, Platform platform) throws Exception {
        mockMvc.perform(post("/api/v1/notifications/devices")
                        .with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DeviceTokenRequest(token, platform))))
                .andExpect(status().isCreated());
    }

    private UserFixture createUserFixture(String suffix) {
        Tenant tenant = tenantRepository.save(Tenant.ofPersonal("Tenant " + suffix, "tenant-" + suffix));
        User user = User.ofOAuth(
                suffix + "@example.com",
                suffix,
                "User " + suffix,
                "https://example.com/avatar.png");
        user.updateDefaultTenantId(tenant.getId());
        User saved = userRepository.save(user);
        return new UserFixture(saved.getId(), tenant.getId());
    }

    private static String postgresJdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(POSTGRES_PORT),
                POSTGRES_DATABASE);
    }

    private record UserFixture(UUID userId, UUID tenantId) {
    }
}
