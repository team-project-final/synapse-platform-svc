package com.synapse.platform.auth.entity;

import java.io.Serializable;
import java.util.UUID;

public record TenantMemberId(UUID tenantId, UUID userId) implements Serializable {
}
