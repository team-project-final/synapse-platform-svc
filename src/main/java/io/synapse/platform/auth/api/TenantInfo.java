package io.synapse.platform.auth.api;

import java.util.UUID;

public record TenantInfo(UUID id, String plan, String status) {
}
