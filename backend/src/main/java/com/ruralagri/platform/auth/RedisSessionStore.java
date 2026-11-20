package com.ruralagri.platform.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;

public class RedisSessionStore implements SessionStore {
    private static final String PREFIX = "rural-agri:auth:session:";
    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisSessionStore(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void register(String jti, long accountId, Instant expiresAt) {
        Duration remaining = Duration.between(clock.instant(), expiresAt);
        if (remaining.isZero() || remaining.isNegative()) {
            throw AuthErrors.dependencyUnavailable();
        }
        try {
            redis.opsForValue().set(PREFIX + jti, Long.toString(accountId), remaining);
        } catch (DataAccessException exception) {
            throw AuthErrors.dependencyUnavailable();
        }
    }

    @Override
    public Optional<String> findAccountId(String jti) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(PREFIX + jti));
        } catch (DataAccessException exception) {
            throw AuthErrors.dependencyUnavailable();
        }
    }

    @Override
    public void revoke(String jti) {
        try {
            redis.delete(PREFIX + jti);
        } catch (DataAccessException exception) {
            throw AuthErrors.dependencyUnavailable();
        }
    }
}
