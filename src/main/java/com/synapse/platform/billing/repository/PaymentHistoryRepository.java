package com.synapse.platform.billing.repository;

import com.synapse.platform.billing.entity.PaymentHistory;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID> {
    Page<PaymentHistory> findByTenantId(UUID tenantId, Pageable pageable);

    java.util.Optional<PaymentHistory> findByIdAndTenantId(UUID id, UUID tenantId);
}
