package io.synapse.platform.billing.repository;

import io.synapse.platform.billing.domain.PaymentHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID> {
}
