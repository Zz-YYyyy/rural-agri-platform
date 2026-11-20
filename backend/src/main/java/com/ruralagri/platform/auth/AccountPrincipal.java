package com.ruralagri.platform.auth;

public record AccountPrincipal(long accountId, String jti, AccountView account) {
}
