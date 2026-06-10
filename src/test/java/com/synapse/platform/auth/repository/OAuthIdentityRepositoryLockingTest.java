package com.synapse.platform.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class OAuthIdentityRepositoryLockingTest {

    @Test
    void findAllByUserIdForUpdate_shouldUsePessimisticWriteLock() throws Exception {
        Method method = OAuthIdentityRepository.class.getMethod("findAllByUserIdForUpdate", UUID.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
