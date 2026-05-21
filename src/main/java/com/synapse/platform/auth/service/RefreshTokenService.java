package com.synapse.platform.auth.service;

import com.synapse.platform.auth.entity.RefreshToken;
import com.synapse.platform.auth.exception.UnauthorizedTokenException;
import com.synapse.platform.auth.repository.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private static final int MAX_ACTIVE_SESSIONS = 5;

    private final RefreshTokenRepository repository;
    private final StringRedisTemplate redisTemplate;
    private final RefreshTokenSessionLock sessionLock;

    public RefreshTokenService(
            RefreshTokenRepository repository,
            StringRedisTemplate redisTemplate,
            RefreshTokenSessionLock sessionLock) {
        this.repository = repository;
        this.redisTemplate = redisTemplate;
        this.sessionLock = sessionLock;
    }

    @Transactional
    public void save(UUID userId, String refreshToken) {
        save(userId, refreshToken, null, null);
    }

    @Transactional
    public void save(UUID userId, String refreshToken, String deviceFingerprint, String ipAddress) {
        sessionLock.acquire(userId);
        evictOldestSessionsIfNecessary(userId);
        store(userId, refreshToken, deviceFingerprint, ipAddress);
    }

    private void store(UUID userId, String refreshToken, String deviceFingerprint, String ipAddress) {
        String tokenHash = RefreshToken.hash(refreshToken);
        RefreshToken entity = RefreshToken.of(
                userId,
                refreshToken,
                deviceFingerprint,
                ipAddress,
                Instant.now().plus(REFRESH_TOKEN_TTL));
        repository.save(entity);
        afterCommit(() -> redisTemplate.opsForValue()
                .set(key(userId, tokenHash), tokenHash, REFRESH_TOKEN_TTL));
    }

    public Optional<String> get(UUID userId) {
        Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
        if (keys == null || keys.isEmpty()) {
            return Optional.empty();
        }
        return keys.stream()
                .findFirst()
                .map(key -> redisTemplate.opsForValue().get(key));
    }

    @Transactional
    public void delete(UUID userId) {
        sessionLock.acquire(userId);
        repository.deleteAllByUserId(userId);
        afterCommit(() -> {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + userId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        });
    }

    @Transactional
    public void rotate(UUID userId, String oldRefreshToken, String newRefreshToken) {
        sessionLock.acquire(userId);
        String oldTokenHash = RefreshToken.hash(oldRefreshToken);
        RefreshToken existingToken = repository.findByUserIdAndTokenHash(userId, oldTokenHash)
                .orElseThrow(() -> new UnauthorizedTokenException("Refresh token does not match stored token"));
        String deviceFingerprint = existingToken.getDeviceFingerprint();
        String ipAddress = existingToken.getIpAddress();
        deleteToken(userId, oldTokenHash);
        store(userId, newRefreshToken, deviceFingerprint, ipAddress);
    }

    public boolean isValid(UUID userId, String token) {
        String tokenHash = RefreshToken.hash(token);
        String cachedHash = redisTemplate.opsForValue().get(key(userId, tokenHash));
        if (cachedHash != null) {
            return cachedHash.equals(tokenHash);
        }
        return repository.existsByUserIdAndTokenHashAndExpiresAtAfter(userId, tokenHash, Instant.now());
    }

    private void evictOldestSessionsIfNecessary(UUID userId) {
        List<RefreshToken> tokens = repository.findAllByUserIdOrderByCreatedAtAsc(userId);
        int tokensToDelete = Math.max(0, tokens.size() - MAX_ACTIVE_SESSIONS + 1);
        tokens.stream()
                .limit(tokensToDelete)
                .forEach(token -> deleteToken(userId, token.getTokenHash()));
    }

    private void deleteToken(UUID userId, String tokenHash) {
        repository.deleteByUserIdAndTokenHash(userId, tokenHash);
        afterCommit(() -> redisTemplate.delete(key(userId, tokenHash)));
    }

    private String key(UUID userId, String tokenHash) {
        return KEY_PREFIX + userId + ":" + tokenHash;
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
