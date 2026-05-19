package com.synapse.platform.auth.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class RefreshTokenService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final String KEY_PREFIX = "refresh:";

    private final RefreshTokenRepository repository;
    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            StringRedisTemplate redisTemplate) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void save(UUID userId, String refreshToken) {
        save(userId, refreshToken, null, null);
    }

    @Transactional
    public void save(UUID userId, String refreshToken, String deviceFingerprint, String ipAddress) {
        repository.deleteAllByUserId(userId);
        store(userId, refreshToken, deviceFingerprint, ipAddress);
    }

    private void store(UUID userId, String refreshToken, String deviceFingerprint, String ipAddress) {
        RefreshToken entity = RefreshToken.of(
                userId,
                refreshToken,
                deviceFingerprint,
                ipAddress,
                Instant.now().plus(REFRESH_TOKEN_TTL));
        repository.save(entity);
        afterCommit(() -> redisTemplate.opsForValue()
                .set(key(userId), RefreshToken.hash(refreshToken), REFRESH_TOKEN_TTL));
    }

    public Optional<String> get(UUID userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    @Transactional
    public void delete(UUID userId) {
        repository.deleteAllByUserId(userId);
        afterCommit(() -> redisTemplate.delete(key(userId)));
    }

    @Transactional
    public void rotate(UUID userId, String newRefreshToken) {
        delete(userId);
        store(userId, newRefreshToken, null, null);
    }

    public boolean isValid(UUID userId, String token) {
        String tokenHash = RefreshToken.hash(token);
        String cachedHash = redisTemplate.opsForValue().get(key(userId));
        if (cachedHash != null) {
            return cachedHash.equals(tokenHash);
        }
        return repository.existsByUserIdAndTokenHashAndExpiresAtAfter(userId, tokenHash, Instant.now());
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
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
