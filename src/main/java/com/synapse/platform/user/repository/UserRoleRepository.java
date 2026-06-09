package com.synapse.platform.user.repository;

import com.synapse.platform.user.entity.UserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findAllByUserIdOrderByCreatedAtAsc(UUID userId);
}
