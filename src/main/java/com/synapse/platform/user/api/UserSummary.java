package com.synapse.platform.user.api;

import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        String displayName
) {
}
