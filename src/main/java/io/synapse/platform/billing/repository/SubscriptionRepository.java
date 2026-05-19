package io.synapse.platform.billing.repository;

import io.synapse.platform.billing.domain.Subscription;
import io.synapse.platform.billing.domain.SubscriptionStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    Optional<Subscription> findByTenantIdAndStatus(UUID tenantId, SubscriptionStatus status);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
