package com.synapse.platform.billing.dto.response;

import com.synapse.platform.billing.entity.PaymentHistory;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentHistoryResponse(
        UUID id,
        UUID subscriptionId,
        Integer amount,
        String currency,
        String status,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt,
        boolean receiptAvailable
) {

    public static PaymentHistoryResponse from(PaymentHistory paymentHistory) {
        return new PaymentHistoryResponse(
                paymentHistory.getId(),
                paymentHistory.getSubscriptionId(),
                paymentHistory.getAmount(),
                paymentHistory.getCurrency(),
                paymentHistory.getStatus(),
                paymentHistory.getPaidAt(),
                paymentHistory.getCreatedAt(),
                paymentHistory.isReceiptAvailable());
    }
}
