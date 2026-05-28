package com.synapse.platform.user.dto.response;

import com.synapse.platform.user.entity.User;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String displayName,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime suspendedAt
) {
    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus().toDbValue(),
                user.getCreatedAt(),
                user.getSuspendedAt());
    }
}
