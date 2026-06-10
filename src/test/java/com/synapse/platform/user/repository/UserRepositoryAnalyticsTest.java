package com.synapse.platform.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserRepositoryAnalyticsTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void analyticsCounts_shouldUseStatusWindowsAndIncludeSoftDeletedTotal() {
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-06-10T00:00:00+09:00");
        long totalBaseline = userRepository.countAllIncludingSoftDeleted();
        long deletedBaseline = userRepository.countDeletedIncludingSoftDeleted();
        long activeBaseline = userRepository.countByStatus(UserStatus.ACTIVE);
        long suspendedBaseline = userRepository.countByStatus(UserStatus.SUSPENDED);
        long newTodayBaseline = userRepository.countByCreatedAtGreaterThanEqual(dayStart);
        long dauBaseline = userRepository.countByStatusAndLastLoginAtGreaterThanEqual(UserStatus.ACTIVE, dayStart);

        User activeRecent = userRepository.save(user("active-recent"));
        ReflectionTestUtils.setField(activeRecent, "createdAt", dayStart.plusHours(1));
        activeRecent.recordSuccessfulLogin(dayStart.plusHours(2));

        User activeOld = userRepository.save(user("active-old"));
        ReflectionTestUtils.setField(activeOld, "createdAt", dayStart.minusDays(2));
        activeOld.recordSuccessfulLogin(dayStart.minusDays(2));

        User suspendedToday = userRepository.save(user("suspended-today"));
        ReflectionTestUtils.setField(suspendedToday, "createdAt", dayStart.plusHours(3));
        suspendedToday.suspend();

        User deleted = userRepository.saveAndFlush(user("deleted"));
        deleted.softDelete();
        userRepository.flush();

        assertThat(userRepository.countAllIncludingSoftDeleted()).isEqualTo(totalBaseline + 4);
        assertThat(userRepository.countDeletedIncludingSoftDeleted()).isEqualTo(deletedBaseline + 1);
        assertThat(userRepository.countByStatus(UserStatus.ACTIVE)).isEqualTo(activeBaseline + 2);
        assertThat(userRepository.countByStatus(UserStatus.SUSPENDED)).isEqualTo(suspendedBaseline + 1);
        assertThat(userRepository.countByCreatedAtGreaterThanEqual(dayStart)).isEqualTo(newTodayBaseline + 2);
        assertThat(userRepository.countByStatusAndLastLoginAtGreaterThanEqual(UserStatus.ACTIVE, dayStart))
                .isEqualTo(dauBaseline + 1);
    }

    private static User user(String username) {
        return User.ofEmailPassword(username + "@example.com", username, "$2a$hash", UUID.randomUUID());
    }
}
