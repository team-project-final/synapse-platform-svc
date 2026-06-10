package com.synapse.platform.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class MfaBackupCodeRepositoryLockingTest {

    @Test
    void findAllUnusedByUserIdForUpdate_shouldUsePessimisticWriteLock() throws Exception {
        Method method = MfaBackupCodeRepository.class.getMethod(
                "findAllUnusedByUserIdForUpdate",
                UUID.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
