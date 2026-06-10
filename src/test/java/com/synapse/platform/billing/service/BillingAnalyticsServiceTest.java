package com.synapse.platform.billing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.synapse.platform.billing.api.BillingAnalyticsSnapshot;
import com.synapse.platform.billing.entity.SubscriptionStatus;
import com.synapse.platform.billing.repository.PaymentHistoryRepository;
import com.synapse.platform.billing.repository.SubscriptionRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BillingAnalyticsServiceTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentHistoryRepository paymentHistoryRepository;

    @Test
    void getBillingAnalytics_shouldCountActiveSubscriptionsAndTodayPayments() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-10T12:00:00+09:00");
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-06-10T00:00:00+09:00");
        given(subscriptionRepository.countByStatus(SubscriptionStatus.ACTIVE)).willReturn(2L);
        given(paymentHistoryRepository.countByStatusAndPaidAtGreaterThanEqual("succeeded", dayStart))
                .willReturn(1L);
        given(paymentHistoryRepository.sumAmountByStatusAndPaidAtGreaterThanEqual("succeeded", dayStart))
                .willReturn(9900L);

        BillingAnalyticsSnapshot result = new BillingAnalyticsService(
                subscriptionRepository,
                paymentHistoryRepository)
                .getBillingAnalytics(now);

        assertThat(result.activeSubscriptions()).isEqualTo(2);
        assertThat(result.paidPaymentsToday()).isEqualTo(1);
        assertThat(result.revenueTodayMinorUnits()).isEqualTo(9900);
    }
}
