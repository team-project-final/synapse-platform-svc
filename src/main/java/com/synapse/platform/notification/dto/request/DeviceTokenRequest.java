package com.synapse.platform.notification.dto.request;

import com.synapse.platform.notification.entity.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DeviceTokenRequest(
        @NotBlank String token,
        @NotNull Platform platform
) {
}
