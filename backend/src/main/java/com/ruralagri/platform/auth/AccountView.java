package com.ruralagri.platform.auth;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record AccountView(String id, String username, String nickname, String avatarUrl,
                          boolean isAdmin, boolean isFarmer, String createdAt,
                          String farmerId, String farmerStatus) {
    private static final DateTimeFormatter UTC_MILLIS =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    public static AccountView from(StoredAccount account, String publicBaseUrl) {
        // Upload/download routes are not implemented yet. A public built-in image is always available.
        return new AccountView(Long.toString(account.id()), account.username(), account.nickname(),
                publicBaseUrl + "/assets/default-avatar.svg", account.admin(), account.farmerId() != null,
                UTC_MILLIS.format(account.createdAt().toInstant(ZoneOffset.UTC)),
                account.farmerId() == null ? null : account.farmerId().toString(), account.farmerStatus());
    }
}
