package com.synapse.platform.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.auth.entity.TenantMember;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
class TenantMemberRepositoryTest {

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
    private TenantMemberRepository tenantMemberRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", TenantMemberRepositoryTest::postgresJdbcUrl);
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
        jdbcTemplate.update("DELETE FROM tenant_members");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM tenants");
    }

    @Test
    void findByTenantId_shouldReturnOnlyTenantMembers() {
        UUID tenantId = createTenant("tenant-a");
        UUID otherTenantId = createTenant("tenant-b");
        UUID ownerId = createUser("owner", tenantId);
        UUID memberId = createUser("member", tenantId);
        UUID otherUserId = createUser("other", otherTenantId);
        createMember(tenantId, ownerId, "owner");
        createMember(tenantId, memberId, "member");
        createMember(otherTenantId, otherUserId, "owner");

        var result = tenantMemberRepository.findByTenantId(tenantId, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent())
                .extracting(TenantMember::getUserId)
                .containsExactlyInAnyOrder(ownerId, memberId);
    }

    @Test
    void findByTenantIdWithSort_shouldReturnOnlyTenantMembers() {
        UUID tenantId = createTenant("tenant-a");
        UUID otherTenantId = createTenant("tenant-b");
        UUID ownerId = createUser("owner", tenantId);
        UUID memberId = createUser("member", tenantId);
        UUID otherUserId = createUser("other", otherTenantId);
        createMember(tenantId, ownerId, "owner");
        createMember(tenantId, memberId, "member");
        createMember(otherTenantId, otherUserId, "owner");

        var result = tenantMemberRepository.findByTenantId(
                tenantId,
                Sort.by(
                        Sort.Order.asc("joinedAt"),
                        Sort.Order.asc("userId")));

        assertThat(result)
                .extracting(TenantMember::getUserId)
                .containsExactlyInAnyOrder(ownerId, memberId);
    }

    @Test
    void findByTenantIdAndUserId_shouldRequireSameTenant() {
        UUID tenantId = createTenant("tenant-a");
        UUID otherTenantId = createTenant("tenant-b");
        UUID userId = createUser("owner", tenantId);
        createMember(tenantId, userId, "owner");

        assertThat(tenantMemberRepository.findByTenantIdAndUserId(tenantId, userId))
                .isPresent();
        assertThat(tenantMemberRepository.findByTenantIdAndUserId(otherTenantId, userId))
                .isEmpty();
    }

    @Test
    void countByTenantIdAndRole_shouldCountOwnersInTenant() {
        UUID tenantId = createTenant("tenant-a");
        UUID otherTenantId = createTenant("tenant-b");
        UUID ownerId = createUser("owner", tenantId);
        UUID coOwnerId = createUser("co-owner", tenantId);
        UUID otherOwnerId = createUser("other-owner", otherTenantId);
        createMember(tenantId, ownerId, "owner");
        createMember(tenantId, coOwnerId, "owner");
        createMember(otherTenantId, otherOwnerId, "owner");

        assertThat(tenantMemberRepository.countByTenantIdAndRole(tenantId, "owner")).isEqualTo(2);
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

    private void createMember(UUID tenantId, UUID userId, String role) {
        jdbcTemplate.update("""
                INSERT INTO tenant_members (tenant_id, user_id, role, joined_at)
                VALUES (?, ?, ?, NOW())
                """, tenantId, userId, role);
    }

    private static String postgresJdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(POSTGRES_PORT),
                POSTGRES_DATABASE);
    }
}
