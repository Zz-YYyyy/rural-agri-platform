package com.ruralagri.platform.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthProperties(String jwtSecretBase64, String issuer, String audience,
                             String publicBaseUrl, int registerPerMinute,
                             int loginIpPerMinute, int loginAccountPerMinute) {
    public AuthProperties {
        if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
            throw new IllegalArgumentException("JWT issuer and audience are required");
        }
        if (publicBaseUrl == null || !publicBaseUrl.matches("https?://[^\\s]+")) {
            throw new IllegalArgumentException("PUBLIC_BASE_URL must be an HTTP(S) URL");
        }
        publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        if (registerPerMinute < 1 || loginIpPerMinute < 1 || loginAccountPerMinute < 1) {
            throw new IllegalArgumentException("Authentication rate limits must be positive");
        }
    }
}
