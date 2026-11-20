package com.ruralagri.platform.auth;

import java.util.UUID;

import com.ruralagri.platform.auth.AuthRequests.LoginRequest;
import com.ruralagri.platform.auth.AuthRequests.RegisterRequest;
import com.ruralagri.platform.common.web.BusinessException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

public class AuthService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final SessionStore sessions;
    private final AuthProperties properties;
    private final String dummyPasswordHash;

    public AuthService(AccountRepository accounts, PasswordEncoder passwords, JwtService jwt,
                       SessionStore sessions, AuthProperties properties) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.jwt = jwt;
        this.sessions = sessions;
        this.properties = properties;
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public AccountView register(RegisterRequest request) {
        try {
            long id = accounts.insertOrdinaryAccount(request.username(), passwords.encode(request.password()),
                    request.nickname());
            return toView(accounts.findById(id).orElseThrow(AuthErrors::dependencyUnavailable));
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(HttpStatus.CONFLICT, "USERNAME_EXISTS", "账号已存在");
        } catch (DataAccessException exception) {
            throw AuthErrors.dependencyUnavailable();
        }
    }

    public LoginResponse login(LoginRequest request) {
        try {
            StoredAccount account = accounts.findByUsername(request.username()).orElse(null);
            boolean matches = passwords.matches(request.password(),
                    account == null ? dummyPasswordHash : account.passwordHash());
            if (account == null || !matches) {
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误");
            }
            requireActive(account);
            accounts.updateLastLogin(account.id());
            var issued = jwt.issue(account.id());
            // Register the independently revocable session before returning any usable token.
            sessions.register(issued.jti(), account.id(), issued.expiresAt());
            return new LoginResponse(issued.value(), "Bearer", JwtService.LIFETIME_SECONDS, toView(account));
        } catch (DataAccessException exception) {
            throw AuthErrors.dependencyUnavailable();
        }
    }

    public StoredAccount currentAccount(long id) {
        try {
            StoredAccount account = accounts.findById(id).orElseThrow(AuthErrors::unauthenticated);
            requireActive(account);
            return account;
        } catch (DataAccessException exception) {
            throw AuthErrors.dependencyUnavailable();
        }
    }

    public AccountView toView(StoredAccount account) {
        return AccountView.from(account, properties.publicBaseUrl());
    }

    public void logout(String jti) {
        sessions.revoke(jti);
    }

    private void requireActive(StoredAccount account) {
        if (!"ACTIVE".equals(account.status())) {
            throw AuthErrors.accountDisabled();
        }
    }

    public record LoginResponse(String accessToken, String tokenType, long expiresIn, AccountView user) {
        @Override
        public String toString() {
            return "LoginResponse[REDACTED]";
        }
    }
}
