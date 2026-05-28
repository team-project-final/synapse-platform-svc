package com.synapse.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.audit.entity.AuditLog;
import com.synapse.platform.audit.repository.AuditLogRepository;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class AuditLogPostgresSchemaTest {

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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", AuditLogPostgresSchemaTest::postgresJdbcUrl);
        registry.add("spring.datasource.username", () -> POSTGRES_USERNAME);
        registry.add("spring.datasource.password", () -> POSTGRES_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
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

    @Test
    void auditLogSchema_shouldValidateAndPersistWithPostgresTypes() {
        AuditLog saved = auditLogRepository.saveAndFlush(AuditLog.of(
                UUID.randomUUID(),
                "USER_REGISTERED",
                UUID.randomUUID(),
                "USER",
                "user-1",
                "{\"email\":\"new@example.com\"}"));

        assertThat(saved.getId()).isNotNull();
        assertThat(columnType("old_value")).isEqualTo("jsonb");
        assertThat(columnType("new_value")).isEqualTo("jsonb");
        assertThat(columnType("ip_address")).isEqualTo("character varying");
    }

    private String columnType(String columnName) {
        return jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_name = 'audit_logs' AND column_name = ?
                """, String.class, columnName);
    }

    private static String postgresJdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(POSTGRES_PORT),
                POSTGRES_DATABASE);
    }
}
