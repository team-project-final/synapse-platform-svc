package com.synapse.platform.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminDataRequestActionRequest(
        @NotNull
        Action action,

        @Size(max = 1000)
        String reason
) {

    public enum Action {
        APPROVE,
        EXECUTE,
        REJECT
    }
}
