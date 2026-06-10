package com.synapse.platform.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.LockModeType;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Lock;

class PasswordResetRequestRepositoryLockingTest {

    @Test
    void findFirstByEmailAndStatusOrderByCreatedAtDesc_shouldUsePessimisticWriteLock() throws Exception {
        Method method = PasswordResetRequestRepository.class.getMethod(
                "findFirstByEmailAndStatusOrderByCreatedAtDesc",
                String.class,
                String.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void findByResetTokenHashAndStatus_shouldUsePessimisticWriteLock() throws Exception {
        Method method = PasswordResetRequestRepository.class.getMethod(
                "findByResetTokenHashAndStatus",
                String.class,
                String.class);

        Lock lock = method.getAnnotation(Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }
}
