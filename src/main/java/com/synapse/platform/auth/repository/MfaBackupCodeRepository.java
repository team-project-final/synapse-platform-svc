package com.synapse.platform.auth.repository;

import com.synapse.platform.auth.entity.MfaBackupCode;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select code from MfaBackupCode code where code.userId = :userId and code.usedAt is null")
    List<MfaBackupCode> findAllUnusedByUserIdForUpdate(@Param("userId") UUID userId);
}
