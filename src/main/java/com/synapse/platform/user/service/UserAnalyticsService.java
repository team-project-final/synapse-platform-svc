package com.synapse.platform.user.service;

import com.synapse.platform.user.api.UserAnalyticsApi;
import com.synapse.platform.user.api.UserAnalyticsSnapshot;
import com.synapse.platform.user.entity.UserStatus;
import com.synapse.platform.user.repository.UserRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAnalyticsService implements UserAnalyticsApi {

    private static final String ACTIVITY_SOURCE = "USERS_LAST_LOGIN_AT";

    private final UserRepository userRepository;

    public UserAnalyticsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserAnalyticsSnapshot getUserAnalytics(OffsetDateTime now) {
        OffsetDateTime dayStart = startOfDay(now);
        OffsetDateTime monthStart = now.minusDays(30);
        return new UserAnalyticsSnapshot(
                userRepository.countAllIncludingSoftDeleted(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.SUSPENDED),
                userRepository.countDeletedIncludingSoftDeleted(),
                userRepository.countByCreatedAtGreaterThanEqual(dayStart),
                userRepository.countByStatusAndLastLoginAtGreaterThanEqual(UserStatus.ACTIVE, dayStart),
                userRepository.countByStatusAndLastLoginAtGreaterThanEqual(UserStatus.ACTIVE, monthStart),
                ACTIVITY_SOURCE);
    }

    private OffsetDateTime startOfDay(OffsetDateTime now) {
        return now.toLocalDate().atStartOfDay().atOffset(now.getOffset());
    }
}
