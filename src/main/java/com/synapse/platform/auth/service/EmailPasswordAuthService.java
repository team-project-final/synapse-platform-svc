package com.synapse.platform.auth.service;

import com.synapse.platform.auth.AuthRoles;
import com.synapse.platform.auth.event.UserEventPublisher;
import com.synapse.platform.auth.entity.Tenant;
import com.synapse.platform.auth.entity.TenantMember;
import com.synapse.platform.auth.exception.AccountLockedException;
import com.synapse.platform.auth.exception.EmailAlreadyExistsException;
import com.synapse.platform.auth.exception.InvalidEmailPasswordLoginException;
import com.synapse.platform.auth.exception.OAuthAccountPasswordLoginException;
import com.synapse.platform.auth.repository.TenantMemberRepository;
import com.synapse.platform.auth.repository.TenantRepository;
import com.synapse.platform.auth.util.SlugGenerator;
import com.synapse.platform.user.api.EmailPasswordUserCreateCommand;
import com.synapse.platform.user.api.LoginFailureResult;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserInfo;
import com.synapse.platform.user.api.UserLoginCredential;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailPasswordAuthService {

    private static final int USERNAME_MAX_LENGTH = 50;
    private static final int USERNAME_BASE_WITH_SUFFIX_MAX_LENGTH = 46;
    private static final int USERNAME_SUFFIX_BOUND = 10_000;
    private static final int USERNAME_MAX_ATTEMPTS = 10;

    private final UserApi userApi;
    private final TenantRepository tenantRepository;
    private final TenantMemberRepository tenantMemberRepository;
    private final SlugGenerator slugGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserEventPublisher userEventPublisher;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailPasswordAuthService(
            UserApi userApi,
            TenantRepository tenantRepository,
            TenantMemberRepository tenantMemberRepository,
            SlugGenerator slugGenerator,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenService refreshTokenService,
            UserEventPublisher userEventPublisher) {
        this.userApi = userApi;
        this.tenantRepository = tenantRepository;
        this.tenantMemberRepository = tenantMemberRepository;
        this.slugGenerator = slugGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
        this.userEventPublisher = userEventPublisher;
    }

    @Transactional
    public SignupResult signup(String email, String password) {
        if (userApi.existsByEmail(email)) {
            throw new EmailAlreadyExistsException();
        }

        try {
            String username = availableUsername(email);
            String passwordHash = passwordEncoder.encode(password);
            Tenant tenant = tenantRepository.save(Tenant.ofPersonal(username, slugGenerator.generate(email)));
            UserInfo user = userApi.createForEmailPassword(new EmailPasswordUserCreateCommand(
                    email,
                    username,
                    passwordHash,
                    tenant.getId()));
            tenantMemberRepository.save(TenantMember.ofOwner(tenant.getId(), user.id()));
            userEventPublisher.publishUserRegistered(user.id(), email, user.displayName(), tenant.getId());
            return new SignupResult(user.id());
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyExistsException();
        }
    }

    @Transactional(noRollbackFor = {
            InvalidEmailPasswordLoginException.class,
            AccountLockedException.class
    })
    public LoginResult login(String email, String password) {
        OffsetDateTime now = OffsetDateTime.now();
        UserLoginCredential credential = userApi.findLoginCredentialByEmail(email)
                .orElseThrow(InvalidEmailPasswordLoginException::new);
        if (credential.lockedUntil() != null && credential.lockedUntil().isAfter(now)) {
            throw new AccountLockedException();
        }
        if (credential.passwordHash() == null || credential.passwordHash().isBlank()) {
            throw new OAuthAccountPasswordLoginException();
        }
        if (!passwordEncoder.matches(password, credential.passwordHash())) {
            LoginFailureResult failure = userApi.recordFailedLogin(credential.id(), now);
            if (failure.locked()) {
                throw new AccountLockedException();
            }
            throw new InvalidEmailPasswordLoginException();
        }

        userApi.recordSuccessfulLogin(credential.id(), now);
        String accessToken = jwtTokenProvider.createAccessToken(credential.id(), AuthRoles.DEFAULT_USER_ROLES);
        String refreshToken = jwtTokenProvider.createRefreshToken(credential.id());
        refreshTokenService.save(credential.id(), refreshToken);
        return new LoginResult(accessToken, refreshToken);
    }

    private String availableUsername(String email) {
        String base = usernameBase(email);
        if (!userApi.existsByUsername(base)) {
            return base;
        }

        String suffixBase = truncate(base, USERNAME_BASE_WITH_SUFFIX_MAX_LENGTH);
        for (int attempt = 0; attempt < USERNAME_MAX_ATTEMPTS; attempt++) {
            String candidate = suffixBase + randomFourDigits();
            if (!userApi.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate available username");
    }

    private String usernameBase(String email) {
        String localPart = email.split("@", 2)[0];
        String normalized = localPart.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
        String base = normalized.isBlank() ? "user" : normalized;
        return truncate(base, USERNAME_MAX_LENGTH);
    }

    private String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String randomFourDigits() {
        return String.format("%04d", secureRandom.nextInt(USERNAME_SUFFIX_BOUND));
    }
}
