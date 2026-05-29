package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsBySlug(String slug);

    Page<Tenant> findAllByDeletedAtIsNull(Pageable pageable);

    Optional<Tenant> findByIdAndDeletedAtIsNull(UUID id);
}
