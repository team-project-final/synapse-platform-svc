package com.synapse.platform.auth.service;

import com.synapse.platform.auth.entity.PasswordResetRequest;
import com.synapse.platform.auth.exception.PasswordResetException;
import com.synapse.platform.auth.repository.PasswordResetRequestRepository;
import com.synapse.platform.user.api.UserApi;
import com.synapse.platform.user.api.UserLoginCredential;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PasswordResetService {

    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(15);
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int RESET_TOKEN_BYTES = 32;
    private static final int CODE_BOUND = 1_000_000;

    private final UserApi userApi;
    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetCodeSender passwordResetCodeSender;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            UserApi userApi,
            PasswordResetRequestRepository passwordResetRequestRepository,
            PasswordEncoder passwordEncoder,
            PasswordResetCodeSender passwordResetCodeSender) {
        this.userApi = userApi;
        this.passwordResetRequestRepository = passwordResetRequestRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordResetCodeSender = passwordResetCodeSender;
    }

    @Transactional
    public void request(String email) {
        String normalizedEmail = normalizeEmail(email);
        userApi.findLoginCredentialByEmail(normalizedEmail)
                .filter(this::isResettable)
                .flatMap(credential -> userApi.findById(credential.id()))
                .ifPresent(user -> {
                    String code = generateCode();
                    OffsetDateTime expiresAt = OffsetDateTime.now().plus(CODE_TTL);
                    passwordResetRequestRepository.save(PasswordResetRequest.create(
                            user.id(),
                            normalizedEmail,
                            passwordEncoder.encode(code),
                            expiresAt));
                    afterCommit(() -> passwordResetCodeSender.send(user, code, expiresAt));
                });
    }

    @Transactional
    public PasswordResetResult verify(String email, String code) {
        OffsetDateTime now = OffsetDateTime.now();
        PasswordResetRequest request = passwordResetRequestRepository
                .findFirstByEmailAndStatusOrderByCreatedAtDesc(
                        normalizeEmail(email),
                        PasswordResetRequest.STATUS_PENDING)
                .orElseThrow(PasswordResetException::invalidOrExpired);

        if (request.isExpired(now)) {
            request.markExpired();
            throw PasswordResetException.invalidOrExpired();
        }
        if (!passwordEncoder.matches(normalizeCode(code), request.getCodeHash())) {
            request.recordFailedAttempt(MAX_VERIFY_ATTEMPTS);
            throw PasswordResetException.invalidOrExpired();
        }

        String resetToken = generateResetToken();
        OffsetDateTime expiresAt = now.plus(RESET_TOKEN_TTL);
        request.markVerified(resetToken, expiresAt);
        return new PasswordResetResult(resetToken, expiresAt);
    }

    @Transactional
    public void confirm(String resetToken, String newPassword) {
        OffsetDateTime now = OffsetDateTime.now();
        PasswordResetRequest request = passwordResetRequestRepository
                .findByResetTokenHashAndStatus(
                        PasswordResetRequest.hash(normalizeResetToken(resetToken)),
                        PasswordResetRequest.STATUS_VERIFIED)
                .orElseThrow(PasswordResetException::invalidOrExpired);

        if (!request.isVerified() || request.isExpired(now)) {
            request.markExpired();
            throw PasswordResetException.invalidOrExpired();
        }
        if (!userApi.isLoginAllowed(request.getUserId())) {
            throw PasswordResetException.invalidOrExpired();
        }

        userApi.resetPassword(request.getUserId(), newPassword);
        request.markUsed();
    }

    private boolean isResettable(UserLoginCredential credential) {
        return "active".equals(credential.status())
                && credential.passwordHash() != null
                && !credential.passwordHash().isBlank();
    }

    private String generateCode() {
        return String.format("%06d", secureRandom.nextInt(CODE_BOUND));
    }

    private String generateResetToken() {
        byte[] bytes = new byte[RESET_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        return code == null ? "" : code.trim();
    }

    private String normalizeResetToken(String resetToken) {
        return resetToken == null ? "" : resetToken.trim();
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
