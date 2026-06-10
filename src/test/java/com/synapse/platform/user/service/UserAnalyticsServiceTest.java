package com.synapse.platform.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.synapse.platform.user.api.UserAnalyticsSnapshot;
import com.synapse.platform.user.entity.UserStatus;
import com.synapse.platform.user.repository.UserRepository;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserAnalyticsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void getUserAnalytics_shouldUseStatusAndLastLoginWindows() {
        OffsetDateTime now = OffsetDateTime.parse("2026-06-10T12:00:00+09:00");
        OffsetDateTime dayStart = OffsetDateTime.parse("2026-06-10T00:00:00+09:00");
        given(userRepository.countAllIncludingSoftDeleted()).willReturn(10L);
        given(userRepository.countByStatus(UserStatus.ACTIVE)).willReturn(8L);
        given(userRepository.countByStatus(UserStatus.SUSPENDED)).willReturn(1L);
        given(userRepository.countDeletedIncludingSoftDeleted()).willReturn(1L);
        given(userRepository.countByCreatedAtGreaterThanEqual(dayStart)).willReturn(2L);
        given(userRepository.countByStatusAndLastLoginAtGreaterThanEqual(
                UserStatus.ACTIVE,
                dayStart))
                .willReturn(3L);
        given(userRepository.countByStatusAndLastLoginAtGreaterThanEqual(
                UserStatus.ACTIVE,
                now.minusDays(30)))
                .willReturn(7L);

        UserAnalyticsSnapshot result = new UserAnalyticsService(userRepository).getUserAnalytics(now);

        assertThat(result.total()).isEqualTo(10);
        assertThat(result.newToday()).isEqualTo(2);
        assertThat(result.dau()).isEqualTo(3);
        assertThat(result.mau()).isEqualTo(7);
        assertThat(result.activitySource()).isEqualTo("USERS_LAST_LOGIN_AT");
    }
}
