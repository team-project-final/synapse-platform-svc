package com.synapse.platform.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.synapse.platform.billing.entity.PaymentHistory;
import com.synapse.platform.billing.entity.PlanCode;
import com.synapse.platform.billing.entity.Subscription;
import com.synapse.platform.billing.entity.SubscriptionStatus;
import com.synapse.platform.billing.repository.PaymentHistoryRepository;
import com.synapse.platform.billing.repository.ProcessedEventRepository;
import com.synapse.platform.billing.repository.SubscriptionRepository;
import java.time.OffsetDateTime;
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
    private PaymentHistoryRepository paymentHistoryRepository;

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

    @Test
    void paymentHistory_findByTenantIdReturnsOnlyTenantRowsNewestFirst() {
        UUID tenantId = createTenant();
        UUID otherTenantId = createTenant();
        PaymentHistory older = paymentHistoryRepository.save(PaymentHistory.of(
                tenantId,
                null,
                "pi_old",
                "in_old",
                "https://invoice.stripe.test/in_old",
                "https://invoice.stripe.test/in_old.pdf",
                900,
                "usd",
                "succeeded",
                OffsetDateTime.parse("2026-06-08T00:00:00Z")));
        PaymentHistory newer = paymentHistoryRepository.save(PaymentHistory.of(
                tenantId,
                null,
                "pi_new",
                "in_new",
                "https://invoice.stripe.test/in_new",
                "https://invoice.stripe.test/in_new.pdf",
                1200,
                "usd",
                "succeeded",
                OffsetDateTime.parse("2026-06-09T00:00:00Z")));
        paymentHistoryRepository.save(PaymentHistory.of(
                otherTenantId,
                null,
                "pi_other",
                "in_other",
                "https://invoice.stripe.test/in_other",
                "https://invoice.stripe.test/in_other.pdf",
                1500,
                "usd",
                "succeeded",
                OffsetDateTime.parse("2026-06-10T00:00:00Z")));

        assertThat(paymentHistoryRepository.findByTenantId(tenantId, paymentPage()).getContent())
                .extracting(PaymentHistory::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    @Test
    void paymentHistory_findByIdAndTenantIdHidesOtherTenantRows() {
        UUID tenantId = createTenant();
        UUID otherTenantId = createTenant();
        PaymentHistory payment = paymentHistoryRepository.save(PaymentHistory.of(
                otherTenantId,
                null,
                "pi_other_tenant",
                "in_other_tenant",
                "https://invoice.stripe.test/in_other_tenant",
                "https://invoice.stripe.test/in_other_tenant.pdf",
                1500,
                "usd",
                "succeeded",
                OffsetDateTime.parse("2026-06-09T00:00:00Z")));

        assertThat(paymentHistoryRepository.findByIdAndTenantId(payment.getId(), tenantId)).isEmpty();
        assertThat(paymentHistoryRepository.findByIdAndTenantId(payment.getId(), otherTenantId))
                .hasValueSatisfying(found -> {
                    assertThat(found.getStripeInvoiceId()).isEqualTo("in_other_tenant");
                    assertThat(found.isReceiptAvailable()).isTrue();
                });
    }

    @Test
    void analyticsQueries_shouldCountActiveSubscriptionsAndSucceededPaymentsSinceDayStart() {
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-06-10T00:00:00Z");
        UUID activeTenantId = createTenant();
        UUID canceledTenantId = createTenant();
        UUID paymentTenantId = createTenant();

        subscriptionRepository.save(Subscription.create(activeTenantId, PlanCode.PRO, "cus_active"));
        Subscription canceled = Subscription.create(canceledTenantId, PlanCode.TEAM, "cus_canceled");
        canceled.cancel();
        subscriptionRepository.save(canceled);

        paymentHistoryRepository.save(PaymentHistory.of(
                paymentTenantId,
                null,
                "pi_succeeded_today",
                "in_succeeded_today",
                "https://invoice.stripe.test/in_succeeded_today",
                "https://invoice.stripe.test/in_succeeded_today.pdf",
                1200,
                "usd",
                "succeeded",
                dayStart.plusHours(1)));
        paymentHistoryRepository.save(PaymentHistory.of(
                paymentTenantId,
                null,
                "pi_succeeded_old",
                "in_succeeded_old",
                "https://invoice.stripe.test/in_succeeded_old",
                "https://invoice.stripe.test/in_succeeded_old.pdf",
                700,
                "usd",
                "succeeded",
                dayStart.minusSeconds(1)));
        paymentHistoryRepository.save(PaymentHistory.of(
                paymentTenantId,
                null,
                "pi_failed_today",
                "in_failed_today",
                "https://invoice.stripe.test/in_failed_today",
                "https://invoice.stripe.test/in_failed_today.pdf",
                900,
                "usd",
                "failed",
                dayStart.plusHours(2)));

        assertThat(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)).isOne();
        assertThat(paymentHistoryRepository.countByStatusAndPaidAtGreaterThanEqual("succeeded", dayStart)).isOne();
        assertThat(paymentHistoryRepository.sumAmountByStatusAndPaidAtGreaterThanEqual("succeeded", dayStart))
                .isEqualTo(1200);
    }

    private PageRequest paymentPage() {
        return PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("paidAt").nullsLast(),
                Sort.Order.desc("createdAt")));
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
