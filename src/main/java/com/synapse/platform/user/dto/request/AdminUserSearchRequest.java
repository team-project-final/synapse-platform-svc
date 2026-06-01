package com.synapse.platform.user.dto.request;

public record AdminUserSearchRequest(
        String q,
        String status,
        int page,
        int size
) {
}
