package com.synapse.platform.billing.repository;

import com.synapse.platform.billing.entity.PaymentHistory;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentHistoryRepository extends JpaRepository<PaymentHistory, UUID> {
    Page<PaymentHistory> findByTenantId(UUID tenantId, Pageable pageable);

    java.util.Optional<PaymentHistory> findByIdAndTenantId(UUID id, UUID tenantId);

    long countByStatusAndPaidAtGreaterThanEqual(String status, OffsetDateTime paidAt);

    @Query("""
            select coalesce(sum(payment.amount), 0)
              from PaymentHistory payment
             where payment.status = :status
               and payment.paidAt >= :paidAt
            """)
    long sumAmountByStatusAndPaidAtGreaterThanEqual(
            @Param("status") String status,
            @Param("paidAt") OffsetDateTime paidAt);
}
