package com.ruralagri.platform.auth;

public interface AuthRateLimiter {
    void checkRegistration(String remoteAddress);

    void checkLogin(String remoteAddress, String normalizedUsername);
}
