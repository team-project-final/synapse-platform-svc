package com.synapse.platform.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserStatusChangeRequest(
        @NotBlank
        @Pattern(regexp = "active|suspended")
        String status
) {
}
