package com.synapse.platform.billing.repository;

import com.synapse.platform.billing.entity.PaymentHistory;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID> {
}
