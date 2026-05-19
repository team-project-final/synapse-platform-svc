package com.synapse.platform.billing.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BillingDomainTest {

    @Test
    void planCode_valueReturnsLowercaseName() {
        assertThat(PlanCode.PRO.value()).isEqualTo("pro");
        assertThat(PlanCode.TEAM.value()).isEqualTo("team");
        assertThat(PlanCode.ENTERPRISE.value()).isEqualTo("enterprise");
        assertThat(PlanCode.FREE.value()).isEqualTo("free");
    }

    @Test
    void subscription_activateAndCancelUpdateState() {
        UUID tenantId = UUID.randomUUID();
        OffsetDateTime periodStart = OffsetDateTime.parse("2026-05-19T00:00:00Z");
        OffsetDateTime periodEnd = OffsetDateTime.parse("2026-06-19T00:00:00Z");
        Subscription subscription = Subscription.create(tenantId, PlanCode.PRO, "cus_test");

        subscription.prePersist();
        subscription.activate("sub_test", periodStart, periodEnd);

        assertThat(subscription.getId()).isNotNull();
        assertThat(subscription.getTenantId()).isEqualTo(tenantId);
        assertThat(subscription.getPlanCode()).isEqualTo(PlanCode.PRO);
        assertThat(subscription.getStripeCustomerId()).isEqualTo("cus_test");
        assertThat(subscription.getStripeSubscriptionId()).isEqualTo("sub_test");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodStart()).isEqualTo(periodStart);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(periodEnd);
        assertThat(subscription.getCreatedAt()).isNotNull();

        subscription.cancel();

        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subscription.getCanceledAt()).isNotNull();
    }

    @Test
    void paymentHistory_ofSetsFieldsAndPrePersistSetsIdAndCreatedAt() {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        OffsetDateTime paidAt = OffsetDateTime.parse("2026-05-19T00:00:00Z");

        PaymentHistory paymentHistory = PaymentHistory.of(
                tenantId,
                subscriptionId,
                "pi_test",
                1200,
                "usd",
                "succeeded",
                paidAt);
        paymentHistory.prePersist();

        assertThat(paymentHistory.getId()).isNotNull();
        assertThat(paymentHistory.getTenantId()).isEqualTo(tenantId);
        assertThat(paymentHistory.getSubscriptionId()).isEqualTo(subscriptionId);
        assertThat(paymentHistory.getStripePaymentIntentId()).isEqualTo("pi_test");
        assertThat(paymentHistory.getAmount()).isEqualTo(1200);
        assertThat(paymentHistory.getCurrency()).isEqualTo("usd");
        assertThat(paymentHistory.getStatus()).isEqualTo("succeeded");
        assertThat(paymentHistory.getPaidAt()).isEqualTo(paidAt);
        assertThat(paymentHistory.getCreatedAt()).isNotNull();
    }

    @Test
    void processedEvent_prePersistSetsReceivedAt() throws Exception {
        ProcessedEvent event = new ProcessedEvent();

        event.prePersist();

        Field receivedAt = ProcessedEvent.class.getDeclaredField("receivedAt");
        receivedAt.setAccessible(true);
        assertThat(receivedAt.get(event)).isInstanceOf(OffsetDateTime.class);
    }
}
