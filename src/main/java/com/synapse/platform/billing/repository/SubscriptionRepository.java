package com.synapse.platform.billing.repository;

import com.synapse.platform.billing.entity.Subscription;
import com.synapse.platform.billing.entity.SubscriptionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);

    long countByStatus(SubscriptionStatus status);
}
