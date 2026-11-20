package com.ruralagri.platform.auth;

import java.util.Optional;

public interface AccountRepository {
    Optional<StoredAccount> findByUsername(String username);

    Optional<StoredAccount> findById(long id);

    long insertOrdinaryAccount(String username, String passwordHash, String nickname);

    void updateLastLogin(long id);
}
