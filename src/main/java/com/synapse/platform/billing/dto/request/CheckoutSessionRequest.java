package com.synapse.platform.billing.dto.request;

import com.synapse.platform.billing.entity.PlanCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckoutSessionRequest(
        @NotNull
        PlanCode planCode,

        @NotBlank
        @Size(max = 500)
        String successUrl,

        @NotBlank
        @Size(max = 500)
        String cancelUrl
) {
}
