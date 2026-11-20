package com.ruralagri.platform.auth;

import java.time.LocalDateTime;

public record StoredAccount(long id, String username, String passwordHash, String nickname,
                            String avatarKey, String status, boolean admin, LocalDateTime createdAt,
                            Long farmerId, String farmerStatus) {
    // Never log this record: it holds the password hash read for credential verification.
    @Override
    public String toString() {
        return "StoredAccount[id=" + id + "]";
    }
}
