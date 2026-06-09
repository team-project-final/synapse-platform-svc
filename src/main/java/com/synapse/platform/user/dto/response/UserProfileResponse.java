package com.synapse.platform.user.dto.response;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String language,
        boolean hasPassword
) {
}
