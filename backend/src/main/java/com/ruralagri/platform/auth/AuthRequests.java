package com.ruralagri.platform.auth;

import java.util.Locale;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {
    }

    public record RegisterRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{3,31}$",
                    message = "账号须以字母开头，由4～32位字母、数字或下划线组成") String username,
            @Utf8Password String password,
            @NotBlank(message = "昵称不能为空") @Size(max = 40, message = "昵称最多40个字符") String nickname) {
        public RegisterRequest {
            username = normalizeUsername(username);
            nickname = nickname == null ? null : nickname.trim();
        }

        @Override
        public String toString() {
            return "RegisterRequest[REDACTED]";
        }
    }

    public record LoginRequest(
            @NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{3,31}$",
                    message = "账号格式不正确") String username,
            @Utf8Password String password) {
        public LoginRequest {
            username = normalizeUsername(username);
        }

        @Override
        public String toString() {
            return "LoginRequest[REDACTED]";
        }
    }

    private static String normalizeUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
