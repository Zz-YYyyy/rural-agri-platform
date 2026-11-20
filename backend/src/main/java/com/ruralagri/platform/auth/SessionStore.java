package com.ruralagri.platform.auth;

import java.time.Instant;
import java.util.Optional;

public interface SessionStore {
    void register(String jti, long accountId, Instant expiresAt);

    Optional<String> findAccountId(String jti);

    void revoke(String jti);
}
