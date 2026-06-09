package com.synapse.platform.user.service;

import com.synapse.platform.user.api.EmailPasswordUserCreateCommand;
import com.synapse.platform.user.api.LoginFailureResult;
import com.synapse.platform.user.api.OAuthUserCreateCommand;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserLoginCredential;
import com.synapse.platform.user.api.UserSessionsRevocationRequested;
import com.synapse.platform.user.dto.request.UserProfileUpdateRequest;
import com.synapse.platform.user.dto.response.UserProfileResponse;
import com.synapse.platform.user.entity.User;
import com.synapse.platform.user.entity.UserRole;
import com.synapse.platform.user.entity.UserSettings;
import com.synapse.platform.user.entity.UserStatus;
import com.synapse.platform.user.repository.UserRepository;
import com.synapse.platform.user.repository.UserRoleRepository;
import com.synapse.platform.user.repository.UserSettingsRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService implements UserApi {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;

    public UserService(
            UserRepository userRepository,
            UserSettingsRepository userSettingsRepository,
            UserRoleRepository userRoleRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
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
    public boolean isLoginAllowed(UUID userId) {
        return userRepository.findById(userId)
                .map(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElse(false);
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
    public boolean hasPasswordLogin(UUID userId) {
        return userRepository.findById(userId)
                .map(User::hasPassword)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findRoles(UUID userId) {
        List<String> roles = userRoleRepository.findAllByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(UserRole::getRole)
                .toList();
        if (roles.isEmpty()) {
            return List.of(DEFAULT_ROLE);
        }
        return roles;
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
        userRoleRepository.save(UserRole.of(saved.getId(), DEFAULT_ROLE));
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
        userRoleRepository.save(UserRole.of(saved.getId(), DEFAULT_ROLE));
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

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(UUID userId) {
        User user = findUser(userId);
        UserSettings settings = userSettingsRepository.findById(userId)
                .orElseGet(() -> UserSettings.defaultFor(userId));
        return toProfileResponse(user, settings);
    }

    @Transactional
    public UserProfileResponse updateMyProfile(UUID userId, UserProfileUpdateRequest request) {
        User user = findUser(userId);
        UserSettings settings = userSettingsRepository.findById(userId)
                .orElseGet(() -> userSettingsRepository.save(UserSettings.defaultFor(userId)));
        user.updateProfile(request.displayName().trim());
        settings.updateLocale(request.language().trim());
        return toProfileResponse(user, settings);
    }

    @Transactional
    public void changeMyPassword(UUID userId, String currentPassword, String newPassword) {
        User user = findUser(userId);
        if (!user.hasPassword()) {
            throw UserSelfServiceException.passwordLoginUnavailable();
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw UserSelfServiceException.invalidCurrentPassword();
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        eventPublisher.publishEvent(new UserSessionsRevocationRequested(userId));
    }

    @Transactional
    public void deleteMyAccount(UUID userId) {
        User user = findUser(userId);
        user.softDelete();
        eventPublisher.publishEvent(new UserSessionsRevocationRequested(userId));
    }

    private UserInfo toUserInfo(User user) {
        return new UserInfo(user.getId(), user.getEmail(), user.getDisplayName(), user.getDefaultTenantId());
    }

    private UserProfileResponse toProfileResponse(User user, UserSettings settings) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                settings.getLocale(),
                user.hasPassword());
    }

    private UserLoginCredential toLoginCredential(User user) {
        return new UserLoginCredential(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getStatus().toDbValue(),
                user.getFailedLoginCount(),
                user.getLockedUntil());
    }

    private User findLoginStateUserForUpdate(UUID userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
