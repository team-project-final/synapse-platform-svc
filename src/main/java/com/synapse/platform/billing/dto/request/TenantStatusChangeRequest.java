package com.synapse.platform.billing.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TenantStatusChangeRequest(
        @NotBlank
        @Pattern(regexp = "active|suspended")
        String status) {
}
