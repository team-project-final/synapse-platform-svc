package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.PasswordResetRequest;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface PasswordResetRequestRepository extends JpaRepository<PasswordResetRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetRequest> findFirstByEmailAndStatusOrderByCreatedAtDesc(String email, String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetRequest> findByResetTokenHashAndStatus(String resetTokenHash, String status);
}
