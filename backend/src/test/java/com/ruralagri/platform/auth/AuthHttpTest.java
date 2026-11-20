package com.ruralagri.platform.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruralagri.platform.common.web.GlobalExceptionHandler;
import com.ruralagri.platform.common.web.JsonErrorWriter;
import com.ruralagri.platform.common.web.TraceIdFilter;
import com.ruralagri.platform.config.SecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MVC, security chain, BCrypt and JOSE; only database/Redis boundaries are mocked. */
@SpringBootTest(classes = AuthHttpTest.TestApp.class)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AuthHttpTest {
    private static final String PASSWORD = " keep spaces ";
    private static final Instant START = Instant.parse("2026-11-20T02:00:00Z");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwt;
    @Autowired private PasswordEncoder passwords;
    @Autowired private MutableClock clock;
    @MockitoBean private AccountRepository accounts;
    @MockitoBean private SessionStore sessions;
    @MockitoBean private AuthRateLimiter limits;
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();
    private StoredAccount account;

    @BeforeEach
    void prepare() {
        clock.now = START;
        activeSessions.clear();
        account = account("ACTIVE", false, null, null);
        when(accounts.findByUsername("alice")).thenReturn(Optional.of(account));
        when(accounts.findById(42L)).thenReturn(Optional.of(account));
        when(accounts.insertOrdinaryAccount(anyString(), anyString(), anyString())).thenReturn(42L);
        doAnswer(invocation -> {
            activeSessions.put(invocation.getArgument(0), Long.toString(invocation.getArgument(1)));
            return null;
        }).when(sessions).register(anyString(), anyLong(), any());
        when(sessions.findAccountId(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(activeSessions.get(invocation.getArgument(0))));
        doAnswer(invocation -> { activeSessions.remove(invocation.getArgument(0)); return null; })
                .when(sessions).revoke(anyString());
    }

    @Test
    void registrationNormalizesNamesHashesExactPasswordAndDoesNotLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/register").header("Authorization", "Bearer stale-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "  ALICE  ", "password", PASSWORD,
                                "nickname", "  种植用户  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("42"))
                .andExpect(jsonPath("$.data.isAdmin").value(false))
                .andExpect(jsonPath("$.data.isFarmer").value(false))
                .andExpect(jsonPath("$.data.createdAt").value("2026-11-20T00:00:00.123Z"))
                .andExpect(jsonPath("$.data.avatarUrl").value("http://127.0.0.1:8080/assets/default-avatar.svg"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
        ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
        verify(accounts).insertOrdinaryAccount(eq("alice"), encoded.capture(), eq("种植用户"));
        assertThat(encoded.getValue()).startsWith("$2a$10$");
        assertThat(passwords.matches(PASSWORD, encoded.getValue())).isTrue();
        assertThat(passwords.matches(PASSWORD.trim(), encoded.getValue())).isFalse();
        verify(sessions, never()).register(anyString(), anyLong(), any());
    }

    @Test
    void databaseUniqueConstraintBecomes409() throws Exception {
        when(accounts.insertOrdinaryAccount(anyString(), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("private SQL details"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "alice", "password", PASSWORD,
                                "nickname", "用户"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("USERNAME_EXISTS"));
    }

    @Test
    void passwordsRejectTooManyUtf8BytesAndPrivilegeInjectionIsRejected() throws Exception {
        for (String password : new String[]{"中".repeat(25), "1234567", "😀".repeat(7)}) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("username", "alice", "password", password))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        }
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "alice", "password", PASSWORD,
                                "nickname", "用户", "isAdmin", true))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(accounts, never()).insertOrdinaryAccount(anyString(), anyString(), anyString());
        verify(accounts, never()).findByUsername(anyString());
    }

    @Test
    void absentAndWrongCredentialsHaveSamePublicFailure() throws Exception {
        for (String username : new String[]{"alice", "unknown"}) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("username", username, "password", "wrong-pass"))))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("账号或密码错误"));
        }
        when(accounts.findByUsername("alice")).thenReturn(Optional.of(account("DISABLED", false, null, null)));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "alice", "password", PASSWORD))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    void logoutRevokesOnlyThisSessionAndReplayNeverAuthorizesMe() throws Exception {
        String first = login();
        String second = login();
        assertThat(first).isNotEqualTo(second);
        assertThat(activeSessions).hasSize(2);
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + first))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("alice"));
        for (int repeat = 0; repeat < 2; repeat++) {
            mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + first))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        }
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + first))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + second))
                .andExpect(status().isOk());
        assertThat(activeSessions).hasSize(1);
    }

    @Test
    void meReadsLatestIdentityAndDisabledAccountIsImmediatelyBlocked() throws Exception {
        String token = login();
        when(accounts.findById(42L)).thenReturn(Optional.of(account("ACTIVE", true, 99L, "DISABLED")));
        mvc.perform(get("/api/v1/me?userId=999").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value("42"))
                .andExpect(jsonPath("$.data.isAdmin").value(true))
                .andExpect(jsonPath("$.data.isFarmer").value(true))
                .andExpect(jsonPath("$.data.farmerStatus").value("DISABLED"));
        mvc.perform(get("/api/v1/admin/orders").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        when(accounts.findById(42L)).thenReturn(Optional.of(account("DISABLED", true, 99L, "ACTIVE")));
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
        // A disabled user can still revoke their own token, but no business authority is granted.
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void expiredTamperedAndForeignSessionsCannotAccessOrLogout() throws Exception {
        String token = login();
        for (String badToken : new String[]{"not-a-jwt", token.substring(0, token.lastIndexOf('.') + 1) + "AAAA"}) {
            mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + badToken))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + badToken))
                    .andExpect(status().isUnauthorized());
        }
        String jti = jwt.verify(token).jti();
        activeSessions.put(jti, "999");
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        clock.now = START.plusSeconds(7200);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void redisFailuresBlockLoginMeAndLogoutWithoutFalseSuccess() throws Exception {
        String token = login();
        when(sessions.findAccountId(anyString())).thenThrow(AuthErrors.dependencyUnavailable());
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
        doThrow(AuthErrors.dependencyUnavailable()).when(sessions).register(anyString(), anyLong(), any());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "alice", "password", PASSWORD))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void rateLimitHasRetryAfterAndNoCredentialsAreChecked() throws Exception {
        doThrow(new RateLimitException(12)).when(limits).checkLogin(anyString(), eq("alice"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", "  ALICE ", "password", PASSWORD))))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "12"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        verify(accounts, never()).findByUsername(anyString());
    }

    @Test
    void missingBearerAndDuplicateHeadersCannotAuthenticateAndAvatarIsPublic() throws Exception {
        mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized());
        String token = login();
        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/assets/default-avatar.svg")).andExpect(status().isOk());
    }

    private String login() throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").header("Authorization", "Bearer expired-optional-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", " ALICE ", "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }

    private StoredAccount account(String status, boolean admin, Long farmerId, String farmerStatus) {
        return new StoredAccount(42L, "alice", passwords.encode(PASSWORD), "种植用户", null, status,
                admin, LocalDateTime.parse("2026-11-20T00:00:00.123"), farmerId, farmerStatus);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, RedisAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class, FlywayAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class})
    @Import({SecurityConfiguration.class, AuthController.class, GlobalExceptionHandler.class,
            JsonErrorWriter.class, TraceIdFilter.class})
    static class TestApp {
        @Bean MutableClock clock() { return new MutableClock(); }
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(10); }
        @Bean AuthProperties properties() {
            // Deliberate test-only key; never used by application configuration or production sources.
            return new AuthProperties(Base64.getEncoder().encodeToString(new byte[32]), "test-issuer", "test-client",
                    "http://127.0.0.1:8080", 5, 30, 10);
        }
        @Bean JwtService jwt(AuthProperties properties, Clock clock) { return new JwtService(properties, clock); }
        @Bean AuthService auth(AccountRepository accounts, PasswordEncoder passwords, JwtService jwt,
                               SessionStore sessions, AuthProperties properties) {
            return new AuthService(accounts, passwords, jwt, sessions, properties);
        }
        @Bean BearerSessionFilter bearer(JwtService jwt, SessionStore sessions, AuthService auth, JsonErrorWriter errors) {
            return new BearerSessionFilter(jwt, sessions, auth, errors);
        }
        @Bean FilterRegistrationBean<BearerSessionFilter> registration(BearerSessionFilter filter) {
            var registration = new FilterRegistrationBean<>(filter);
            registration.setEnabled(false);
            return registration;
        }
    }

    static class MutableClock extends Clock {
        Instant now = START;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
