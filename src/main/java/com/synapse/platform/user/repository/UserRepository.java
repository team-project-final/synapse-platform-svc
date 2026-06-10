package com.synapse.platform.user.repository;

import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserStatus;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") UUID userId);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    long countByStatus(UserStatus status);

    long countByCreatedAtGreaterThanEqual(OffsetDateTime createdAt);

    long countByStatusAndLastLoginAtGreaterThanEqual(UserStatus status, OffsetDateTime lastLoginAt);

    @Query(value = "select count(*) from users", nativeQuery = true)
    long countAllIncludingSoftDeleted();

    @Query(value = "select count(*) from users where deleted_at is not null or status = 'deleted'", nativeQuery = true)
    long countDeletedIncludingSoftDeleted();
}
