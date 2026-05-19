package com.synapse.platform.user;

import com.synapse.platform.user.api.OAuthUserCreateCommand;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.domain.User;
import com.synapse.platform.user.domain.UserSettings;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.user.repository.UserSettingsRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserApi {

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;

    public UserService(UserRepository userRepository, UserSettingsRepository userSettingsRepository) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
    }

    @Override
    public Optional<UserInfo> findById(UUID userId) {
        return userRepository.findById(userId).map(this::toUserInfo);
    }

    @Override
    public Optional<UserInfo> findByEmail(String email) {
        return userRepository.findByEmail(email).map(this::toUserInfo);
    }

    @Override
    @Transactional
    public UserInfo createForOAuth(OAuthUserCreateCommand command) {
        User user = User.ofOAuth(
                command.email(),
                command.slug(),
                command.displayName(),
                command.avatarUrl());
        user.updateDefaultTenantId(command.defaultTenantId());
        User saved = userRepository.save(user);
        userSettingsRepository.save(UserSettings.defaultFor(saved.getId()));
        return toUserInfo(saved);
    }

    private UserInfo toUserInfo(User user) {
        return new UserInfo(user.getId(), user.getEmail(), user.getDisplayName(), user.getDefaultTenantId());
    }
}
