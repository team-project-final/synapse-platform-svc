package com.synapse.platform.admin.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record AdminDataRequestPageResponse(
        List<AdminDataRequestResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public AdminDataRequestPageResponse {
        content = List.copyOf(content);
    }

    @Override
    public List<AdminDataRequestResponse> content() {
        return List.copyOf(content);
    }

    public static AdminDataRequestPageResponse from(Page<AdminDataRequestResponse> page) {
        return new AdminDataRequestPageResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
