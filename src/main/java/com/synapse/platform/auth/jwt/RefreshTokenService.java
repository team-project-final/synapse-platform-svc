package com.synapse.platform.auth.jwt;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);
    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    public RefreshTokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void save(UUID userId, String refreshToken) {
        redisTemplate.opsForValue().set(key(userId), refreshToken, REFRESH_TOKEN_TTL);
    }

    public Optional<String> get(UUID userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(userId)));
    }

    public void delete(UUID userId) {
        redisTemplate.delete(key(userId));
    }

    public void rotate(UUID userId, String newRefreshToken) {
        delete(userId);
        save(userId, newRefreshToken);
    }

    public boolean isValid(UUID userId, String token) {
        return get(userId)
                .map(token::equals)
                .orElse(false);
    }

    private String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
