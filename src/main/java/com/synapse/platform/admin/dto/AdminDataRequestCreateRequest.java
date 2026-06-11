package com.synapse.platform.admin.dto;

import com.synapse.platform.admin.entity.GdprDataRequestType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record AdminDataRequestCreateRequest(
        @NotNull
        UUID userId,

        @NotNull
        GdprDataRequestType type,

        @Size(max = 500)
        String reason
) {
}
