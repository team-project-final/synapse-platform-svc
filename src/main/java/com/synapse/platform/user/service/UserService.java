package com.synapse.platform.user.service;

import com.synapse.platform.user.api.EmailPasswordUserCreateCommand;
import com.synapse.platform.user.api.LoginFailureResult;
import com.synapse.platform.user.api.OAuthUserCreateCommand;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserLoginCredential;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserSettings;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.user.repository.UserSettingsRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
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
    public Optional<UserLoginCredential> findLoginCredentialByEmail(String email) {
        return userRepository.findByEmail(email).map(this::toLoginCredential);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
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

    @Override
    @Transactional
    public UserInfo createForEmailPassword(EmailPasswordUserCreateCommand command) {
        User user = User.ofEmailPassword(
                command.email(),
                command.username(),
                command.passwordHash(),
                command.defaultTenantId());
        User saved = userRepository.save(user);
        userSettingsRepository.save(UserSettings.defaultFor(saved.getId()));
        return toUserInfo(saved);
    }

    @Override
    @Transactional
    public LoginFailureResult recordFailedLogin(UUID userId, OffsetDateTime now) {
        User user = findLoginStateUserForUpdate(userId);
        user.recordFailedLogin(now);
        return new LoginFailureResult(user.getFailedLoginCount(), user.getLockedUntil());
    }

    @Override
    @Transactional
    public void recordSuccessfulLogin(UUID userId, OffsetDateTime now) {
        User user = findLoginStateUserForUpdate(userId);
        user.recordSuccessfulLogin(now);
    }

    private UserInfo toUserInfo(User user) {
        return new UserInfo(user.getId(), user.getEmail(), user.getDisplayName(), user.getDefaultTenantId());
    }

    private UserLoginCredential toLoginCredential(User user) {
        return new UserLoginCredential(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFailedLoginCount(),
                user.getLockedUntil());
    }

    private User findLoginStateUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
