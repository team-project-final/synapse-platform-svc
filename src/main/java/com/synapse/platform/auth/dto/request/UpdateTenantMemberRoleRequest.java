package com.synapse.platform.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateTenantMemberRoleRequest(
        @NotBlank
        String role
) {
}
