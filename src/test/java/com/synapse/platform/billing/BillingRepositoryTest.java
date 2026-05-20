package com.synapse.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.synapse.platform.billing.entity.PlanCode;
import com.synapse.platform.billing.entity.Subscription;
import com.synapse.platform.billing.entity.SubscriptionStatus;
import com.synapse.platform.billing.repository.ProcessedEventRepository;
import com.synapse.platform.billing.repository.SubscriptionRepository;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.flyway.enabled=true")
class BillingRepositoryTest {

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
    private SubscriptionRepository subscriptionRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", BillingRepositoryTest::postgresJdbcUrl);
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
        jdbcTemplate.update("DELETE FROM payment_history");
        jdbcTemplate.update("DELETE FROM subscriptions");
        jdbcTemplate.update("DELETE FROM processed_events");
        jdbcTemplate.update("DELETE FROM tenants");
    }

    @Test
    void subscription_saveAndFindByTenantAndActiveStatus() {
        UUID tenantId = createTenant();
        Subscription saved = subscriptionRepository.save(
                Subscription.create(tenantId, PlanCode.PRO, "cus_test"));

        assertThat(subscriptionRepository.findByTenantIdAndStatus(tenantId, SubscriptionStatus.ACTIVE))
                .hasValueSatisfying(subscription -> {
                    assertThat(subscription.getId()).isEqualTo(saved.getId());
                    assertThat(subscription.getPlanCode()).isEqualTo(PlanCode.PRO);
                    assertThat(subscription.getStripeCustomerId()).isEqualTo("cus_test");
                });
    }

    @Test
    void subscription_duplicateActiveSubscriptionForTenantFails() {
        UUID tenantId = createTenant();
        subscriptionRepository.saveAndFlush(Subscription.create(tenantId, PlanCode.PRO, "cus_test_1"));

        assertThatThrownBy(() -> subscriptionRepository.saveAndFlush(
                Subscription.create(tenantId, PlanCode.TEAM, "cus_test_2")))
                .hasMessageContaining("could not execute statement");
    }

    @Test
    @Transactional
    void processedEvent_insertIfAbsentReturnsOneThenZero() {
        int first = processedEventRepository.insertIfAbsent("evt_test", "checkout.session.completed");
        int second = processedEventRepository.insertIfAbsent("evt_test", "checkout.session.completed");

        assertThat(first).isOne();
        assertThat(second).isZero();
    }

    private UUID createTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tenants (
                    id, name, slug, plan, status, tenant_type, region, settings, created_at, updated_at
                ) VALUES (
                    ?, 'Billing Test Tenant', ?, 'free', 'active', 'personal', 'ap-northeast-2', '{}', NOW(), NOW()
                )
                """, tenantId, "billing-test-" + tenantId);
        return tenantId;
    }

    private static String postgresJdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(),
                postgres.getMappedPort(POSTGRES_PORT),
                POSTGRES_DATABASE);
    }
}
