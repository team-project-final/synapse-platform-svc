package com.synapse.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record MfaBackupVerifyRequest(
        @NotBlank
        String code
) {
}
