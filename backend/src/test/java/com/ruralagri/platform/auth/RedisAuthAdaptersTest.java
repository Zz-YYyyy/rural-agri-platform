package com.ruralagri.platform.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import com.ruralagri.platform.common.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisAuthAdaptersTest {
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void sessionTtlMatchesRemainingJwtLifetimeAndRevokeTargetsOnlyOneJti() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var store = new RedisSessionStore(redis, Clock.fixed(NOW.plusMillis(500), ZoneOffset.UTC));
        store.register("first-jti", 42L, NOW.plusSeconds(7200));
        verify(values).set("rural-agri:auth:session:first-jti", "42", Duration.ofMillis(7199500));
        store.revoke("first-jti");
        verify(redis).delete("rural-agri:auth:session:first-jti");
        assertThatThrownBy(() -> store.register("expired", 42L, NOW)).isInstanceOf(BusinessException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisConnectionErrorsBecome503OnEverySessionOperation() {
        var redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        var failure = new RedisConnectionFailureException("private infrastructure details");
        doThrow(failure).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.get(anyString())).thenThrow(failure);
        when(redis.delete(anyString())).thenThrow(failure);
        var store = new RedisSessionStore(redis, Clock.fixed(NOW, ZoneOffset.UTC));
        assertUnavailable(() -> store.register("jti", 42L, NOW.plusSeconds(7200)));
        assertUnavailable(() -> store.findAccountId("jti"));
        assertUnavailable(() -> store.revoke("jti"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void limiterRoundsRetryAfterUpAndFailsClosedWhenRedisHasNoResult() {
        var redis = mock(StringRedisTemplate.class);
        var properties = new AuthProperties("test-only", "issuer", "audience", "http://localhost:8080", 5, 30, 10);
        var limiter = new RedisAuthRateLimiter(redis, properties);
        when(redis.execute(any(RedisScript.class), anyList(), eq("5"), eq("60000"))).thenReturn(1001L);
        assertThatThrownBy(() -> limiter.checkRegistration("127.0.0.1"))
                .isInstanceOfSatisfying(RateLimitException.class, error ->
                        assertThat(error.getRetryAfterSeconds()).isEqualTo(2));
        when(redis.execute(any(RedisScript.class), anyList(), eq("5"), eq("60000"))).thenReturn(null);
        assertUnavailable(() -> limiter.checkRegistration("127.0.0.1"));
        when(redis.execute(any(RedisScript.class), anyList(), eq("5"), eq("60000")))
                .thenThrow(new RedisConnectionFailureException("private infrastructure details"));
        assertUnavailable(() -> limiter.checkRegistration("127.0.0.1"));
    }

    private void assertUnavailable(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class, error -> {
            assertThat(error.getStatus().value()).isEqualTo(503);
            assertThat(error.getCode()).isEqualTo("DEPENDENCY_UNAVAILABLE");
            assertThat(error.getMessage()).doesNotContain("private");
        });
    }
}
