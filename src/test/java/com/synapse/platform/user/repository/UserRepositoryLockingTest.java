package com.synapse.platform.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class UserRepositoryLockingTest {

    @Test
    void findByIdForUpdate_shouldUsePessimisticWriteLock() throws Exception {
        Method method = UserRepository.class.getMethod("findByIdForUpdate", UUID.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
