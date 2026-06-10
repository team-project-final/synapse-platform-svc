package com.synapse.platform.auth.dto.response;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record MyTenantResponse(
        UUID id,
        String name,
        String slug,
        String plan,
        String status,
        String tenantType,
        String region,
        Map<String, Object> settings,
        String myRole,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public MyTenantResponse {
        settings = settings == null ? Map.of() : immutableCopy(settings);
    }

    @Override
    public Map<String, Object> settings() {
        return immutableCopy(settings);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
