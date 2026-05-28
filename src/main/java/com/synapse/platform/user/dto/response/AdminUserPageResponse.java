package com.synapse.platform.user.dto.response;

import java.util.List;
import org.springframework.data.domain.Page;

public record AdminUserPageResponse(
        List<AdminUserResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public AdminUserPageResponse {
        content = List.copyOf(content);
    }

    @Override
    public List<AdminUserResponse> content() {
        return List.copyOf(content);
    }

    public static AdminUserPageResponse from(Page<AdminUserResponse> page) {
        return new AdminUserPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
