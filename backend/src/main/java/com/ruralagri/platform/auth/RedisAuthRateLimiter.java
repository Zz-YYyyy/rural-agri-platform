package com.ruralagri.platform.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class RedisAuthRateLimiter implements AuthRateLimiter {
    private static final DefaultRedisScript<Long> LIMIT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
            if count > tonumber(ARGV[1]) then
                return math.max(1, redis.call('PTTL', KEYS[1]))
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public RedisAuthRateLimiter(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void checkRegistration(String remoteAddress) {
        check("register-ip", remoteAddress, properties.registerPerMinute());
    }

    @Override
    public void checkLogin(String remoteAddress, String normalizedUsername) {
        check("login-ip", remoteAddress, properties.loginIpPerMinute());
        check("login-account", normalizedUsername, properties.loginAccountPerMinute());
    }

    private void check(String bucket, String subject, int maximum) {
        try {
            String key = "rural-agri:auth:limit:" + bucket + ":" + digest(subject);
            Long waitMillis = redis.execute(LIMIT, List.of(key), Integer.toString(maximum), "60000");
            if (waitMillis == null) {
                throw AuthErrors.dependencyUnavailable();
            }
            if (waitMillis > 0) {
                throw new RateLimitException((waitMillis + 999) / 1000);
            }
        } catch (DataAccessException exception) {
            throw AuthErrors.dependencyUnavailable();
        }
    }

    private String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
