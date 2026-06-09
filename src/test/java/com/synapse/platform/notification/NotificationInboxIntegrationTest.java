package com.synapse.platform.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.notification.entity.Notification;
import com.synapse.platform.notification.entity.NotificationChannel;
import com.synapse.platform.notification.repository.NotificationRepository;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.repository.UserRepository;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "app.cors.allowed-origins=http://localhost:3000,http://localhost:5173"
})
class NotificationInboxIntegrationTest {

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
    private NotificationRepository notificationRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private UserRepository userRepository;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", NotificationInboxIntegrationTest::postgresJdbcUrl);
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
        notificationRepository.deleteAll();
        userRepository.deleteAll();
        tenantRepository.deleteAll();
    }

    @Test
    void inboxFlow_shouldListCountAndMarkReadForCurrentUserOnly() throws Exception {
        UserFixture owner = createUserFixture("inbox-owner");
        UserFixture other = createUserFixture("inbox-other");
        Notification ownerUnread = saveSentNotification(owner, NotificationChannel.FCM);
        saveSentNotification(owner, NotificationChannel.EMAIL);
        saveSentNotification(other, NotificationChannel.FCM);

        mockMvc.perform(get("/api/v1/notifications")
                        .with(user(owner.userId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(ownerUnread.getId().toString()))
                .andExpect(jsonPath("$.items[0].read").value(false));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(user(owner.userId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(put("/api/v1/notifications/{id}/read", ownerUnread.getId())
                        .with(user(owner.userId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        assertThat(notificationRepository.findById(ownerUnread.getId()))
                .get()
                .extracting(Notification::isRead)
                .isEqualTo(true);

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .with(user(owner.userId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void readAll_shouldMarkOnlyCurrentUsersSentFcmNotifications() throws Exception {
        UserFixture owner = createUserFixture("read-all-owner");
        UserFixture other = createUserFixture("read-all-other");
        Notification ownerUnread = saveSentNotification(owner, NotificationChannel.FCM);
        Notification ownerEmail = saveSentNotification(owner, NotificationChannel.EMAIL);
        Notification otherUnread = saveSentNotification(other, NotificationChannel.FCM);

        MvcResult result = mockMvc.perform(post("/api/v1/notifications/read-all")
                        .with(user(owner.userId().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(1))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("updatedCount");
        assertThat(notificationRepository.findById(ownerUnread.getId()))
                .get()
                .extracting(Notification::isRead)
                .isEqualTo(true);
        assertThat(notificationRepository.findById(ownerEmail.getId()))
                .get()
                .extracting(Notification::isRead)
                .isEqualTo(false);
        assertThat(notificationRepository.findById(otherUnread.getId()))
                .get()
                .extracting(Notification::isRead)
                .isEqualTo(false);
    }

    private Notification saveSentNotification(UserFixture fixture, NotificationChannel channel) {
        Notification notification = Notification.create(
                UUID.randomUUID(),
                fixture.userId(),
                fixture.tenantId(),
                "CARD_REVIEW_DUE",
                channel,
                "Review due",
                "A card is ready.");
        notification.markSent();
        return notificationRepository.save(notification);
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
