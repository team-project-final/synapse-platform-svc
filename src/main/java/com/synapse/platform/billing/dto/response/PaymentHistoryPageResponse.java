package com.synapse.platform.billing.dto.response;

import com.synapse.platform.billing.entity.PaymentHistory;
import java.util.List;
import org.springframework.data.domain.Page;

public record PaymentHistoryPageResponse(
        List<PaymentHistoryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public PaymentHistoryPageResponse {
        items = List.copyOf(items);
    }

    public static PaymentHistoryPageResponse from(Page<PaymentHistory> page) {
        return new PaymentHistoryPageResponse(
                page.getContent().stream()
                        .map(PaymentHistoryResponse::from)
                        .toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public List<PaymentHistoryResponse> items() {
        return List.copyOf(items);
    }
}
