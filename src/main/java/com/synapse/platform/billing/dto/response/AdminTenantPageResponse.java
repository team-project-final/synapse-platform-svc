package com.synapse.platform.billing.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record AdminTenantPageResponse(
        List<AdminTenantResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public AdminTenantPageResponse {
        content = List.copyOf(content);
    }

    @Override
    public List<AdminTenantResponse> content() {
        return List.copyOf(content);
    }

    public static AdminTenantPageResponse from(Page<AdminTenantResponse> page) {
        return new AdminTenantPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
