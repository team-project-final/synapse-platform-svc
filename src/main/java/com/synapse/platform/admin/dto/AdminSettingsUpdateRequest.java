package com.synapse.platform.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record AdminSettingsUpdateRequest(
        @NotNull
        List<@Valid FeatureFlagUpdate> featureFlags,

        @Valid
        @NotNull
        RateLimitUpdate rateLimit
) {

    public AdminSettingsUpdateRequest {
        if (featureFlags != null) {
            featureFlags = List.copyOf(featureFlags);
        }
    }

    public List<FeatureFlagUpdate> featureFlags() {
        return featureFlags == null ? null : List.copyOf(featureFlags);
    }

    public record FeatureFlagUpdate(
            @NotBlank
            String key,

            @NotNull
            Boolean enabled
    ) {
    }

    public record RateLimitUpdate(
            @Min(1)
            @Max(10000)
            int apiRequestsPerMinute
    ) {
    }
}
