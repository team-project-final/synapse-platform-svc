package io.synapse.platform.billing.dto;

import io.synapse.platform.billing.domain.PlanCode;
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
