package com.synapse.platform.billing.service;

import com.synapse.platform.billing.api.BillingAnalyticsApi;
import com.synapse.platform.billing.api.BillingAnalyticsSnapshot;
import com.synapse.platform.billing.entity.SubscriptionStatus;
import com.synapse.platform.billing.repository.PaymentHistoryRepository;
import com.synapse.platform.billing.repository.SubscriptionRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingAnalyticsService implements BillingAnalyticsApi {

    private static final String SUCCEEDED = "succeeded";

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentHistoryRepository paymentHistoryRepository;

    public BillingAnalyticsService(
            SubscriptionRepository subscriptionRepository,
            PaymentHistoryRepository paymentHistoryRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentHistoryRepository = paymentHistoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public BillingAnalyticsSnapshot getBillingAnalytics(OffsetDateTime now) {
        OffsetDateTime dayStart = startOfDay(now);
        return new BillingAnalyticsSnapshot(
                subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE),
                paymentHistoryRepository.countByStatusAndPaidAtGreaterThanEqual(SUCCEEDED, dayStart),
                paymentHistoryRepository.sumAmountByStatusAndPaidAtGreaterThanEqual(SUCCEEDED, dayStart));
    }

    private OffsetDateTime startOfDay(OffsetDateTime now) {
        return now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
    }
}
