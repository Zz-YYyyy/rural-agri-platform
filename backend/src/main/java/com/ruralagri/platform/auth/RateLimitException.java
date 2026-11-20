package com.ruralagri.platform.auth;

import com.ruralagri.platform.common.web.BusinessException;
import org.springframework.http.HttpStatus;

public class RateLimitException extends BusinessException {
    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "操作过于频繁，请稍后重试");
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
