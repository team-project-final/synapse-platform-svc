package com.synapse.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.repository.UserRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class UserServiceConcurrencyIntegrationTest {

    @Autowired
    private UserApi userApi;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void recordFailedLogin_concurrentFiveAttempts_shouldPersistFiveFailuresAndLock() throws Exception {
        User user = userRepository.save(User.ofEmailPassword(
                "race-" + UUID.randomUUID() + "@example.com",
                "raceuser",
                "$2a$10$hash",
                null));
        CountDownLatch ready = new CountDownLatch(5);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(5);

        try {
            var futures = IntStream.range(0, 5)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                        userApi.recordFailedLogin(user.getId(), OffsetDateTime.now());
                        return null;
                    }))
                    .toList();

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<Object> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            User saved = userRepository.findById(user.getId()).orElseThrow();
            assertThat(saved.getFailedLoginCount()).isEqualTo(5);
            assertThat(saved.getLockedUntil()).isNotNull();
        } finally {
            executor.shutdownNow();
        }
    }
}
