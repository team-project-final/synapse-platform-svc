package com.synapse.platform.auth.dto.request;

import jakarta.validation.constraints.Size;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record UpdateTenantRequest(
        @Size(max = 200)
        String name,
        Map<String, Object> settings
) {
    public UpdateTenantRequest {
        if (settings != null) {
            settings = immutableCopy(settings);
        }
    }

    @Override
    public Map<String, Object> settings() {
        return settings == null ? null : immutableCopy(settings);
    }

    private static Map<String, Object> immutableCopy(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }
}
