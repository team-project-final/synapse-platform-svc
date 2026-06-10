package com.synapse.platform.billing.dto.response;

import com.synapse.platform.billing.entity.PaymentHistory;
import java.util.UUID;

public record BillingReceiptResponse(
        UUID paymentId,
        String stripePaymentIntentId,
        String stripeInvoiceId,
        String invoiceUrl,
        String invoicePdfUrl,
        boolean available
) {

    public static BillingReceiptResponse from(PaymentHistory paymentHistory) {
        return new BillingReceiptResponse(
                paymentHistory.getId(),
                paymentHistory.getStripePaymentIntentId(),
                paymentHistory.getStripeInvoiceId(),
                paymentHistory.getInvoiceUrl(),
                paymentHistory.getInvoicePdfUrl(),
                paymentHistory.isReceiptAvailable());
    }
}
