package com.synapse.platform.admin.dto;

public record AdminDataRequestSearchRequest(
        String status,
        String q,
        int page,
        int size
) {
}
