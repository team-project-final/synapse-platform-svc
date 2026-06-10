package com.synapse.platform.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.synapse.platform.auth.entity.TenantInvitation;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
class TenantInvitationRepositoryTest {

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
    private TenantInvitationRepository tenantInvitationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TenantInvitationRepositoryTest::postgresJdbcUrl);
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
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM tenant_invitations");
        jdbcTemplate.update("DELETE FROM tenant_members");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM tenants");
    }

    @Test
    void findByTenantIdAndEmailAndStatus_shouldReturnPendingInvitation() {
        UUID tenantId = createTenant("tenant-a");
        UUID invitedBy = createUser("owner", tenantId);
        TenantInvitation invitation = TenantInvitation.create(
                tenantId,
                "new@example.com",
                "member",
                "raw-token",
                invitedBy,
                OffsetDateTime.now().plusDays(7));
        tenantInvitationRepository.saveAndFlush(invitation);

        var result = tenantInvitationRepository.findByTenantIdAndEmailAndStatus(
                tenantId,
                "new@example.com",
                TenantInvitation.STATUS_PENDING);

        assertThat(result).isPresent();
        assertThat(result.get().getTokenHash()).hasSize(64);
    }

    @Test
    void saveAndFlush_sameTenantEmailPending_shouldFailUniqueConstraint() {
        UUID tenantId = createTenant("tenant-a");
        UUID invitedBy = createUser("owner", tenantId);
        tenantInvitationRepository.saveAndFlush(TenantInvitation.create(
                tenantId,
                "duplicate@example.com",
                "member",
                "raw-token-1",
                invitedBy,
                OffsetDateTime.now().plusDays(7)));

        assertThatThrownBy(() -> tenantInvitationRepository.saveAndFlush(TenantInvitation.create(
                        tenantId,
                        "duplicate@example.com",
                        "viewer",
                        "raw-token-2",
                        invitedBy,
                        OffsetDateTime.now().plusDays(7))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void saveAndFlush_sameEmailDifferentTenant_shouldSucceed() {
        UUID tenantId = createTenant("tenant-a");
        UUID otherTenantId = createTenant("tenant-b");
        UUID invitedBy = createUser("owner", tenantId);
        UUID otherInvitedBy = createUser("other-owner", otherTenantId);

        tenantInvitationRepository.saveAndFlush(TenantInvitation.create(
                tenantId,
                "shared@example.com",
                "member",
                "raw-token-1",
                invitedBy,
                OffsetDateTime.now().plusDays(7)));
        tenantInvitationRepository.saveAndFlush(TenantInvitation.create(
                otherTenantId,
                "shared@example.com",
                "member",
                "raw-token-2",
                otherInvitedBy,
                OffsetDateTime.now().plusDays(7)));

        assertThat(tenantInvitationRepository.findAll()).hasSize(2);
    }

    private UUID createTenant(String slug) {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (
                    id, name, slug, plan, status, tenant_type, region, settings, created_at, updated_at
                ) VALUES (
                    ?, 'Tenant', ?, 'free', 'active', 'personal', 'ap-northeast-2', '{}', NOW(), NOW()
                )
                """, tenantId, slug);
        return tenantId;
    }

    private UUID createUser(String username, UUID tenantId) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (
                    id, email, username, display_name, default_tenant_id, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?, NOW(), NOW()
                )
                """, userId, username + "@example.com", username, username, tenantId);
        return userId;
    }

    private static String postgresJdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(POSTGRES_PORT),
                POSTGRES_DATABASE);
    }
}
