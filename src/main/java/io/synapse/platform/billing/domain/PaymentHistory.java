package io.synapse.platform.billing.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_history")
public class PaymentHistory {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false)
    private String currency = "usd";

    @Column(nullable = false)
    private String status;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected PaymentHistory() {
    }

    public static PaymentHistory of(
            UUID tenantId,
            UUID subscriptionId,
            String stripePaymentIntentId,
            int amount,
            String currency,
            String status,
            OffsetDateTime paidAt) {
        PaymentHistory paymentHistory = new PaymentHistory();
        paymentHistory.tenantId = tenantId;
        paymentHistory.subscriptionId = subscriptionId;
        paymentHistory.stripePaymentIntentId = stripePaymentIntentId;
        paymentHistory.amount = amount;
        paymentHistory.currency = currency;
        paymentHistory.status = status;
        paymentHistory.paidAt = paidAt;
        return paymentHistory;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrderedEpoch();
        }
        createdAt = OffsetDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public String getStripePaymentIntentId() {
        return stripePaymentIntentId;
    }

    public Integer getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getStatus() {
        return status;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
