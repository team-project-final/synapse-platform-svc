package com.synapse.platform.auth.dto.response;

import java.util.List;

public record TenantMemberPageResponse(
        List<TenantMemberResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public TenantMemberPageResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
