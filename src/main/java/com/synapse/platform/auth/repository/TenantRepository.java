package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.Tenant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsBySlug(String slug);
}
