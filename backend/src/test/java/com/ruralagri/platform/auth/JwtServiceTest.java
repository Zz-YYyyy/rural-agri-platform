package com.ruralagri.platform.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.ruralagri.platform.common.web.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-20T00:00:00Z");
    private static final byte[] KEY = new byte[32];
    private final JwtService service = new JwtService(properties(KEY), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void rejectsWrongSignatureIssuerAudienceAndIncompleteClaims() {
        byte[] anotherKey = new byte[32];
        anotherKey[0] = 1;
        assertRejected(sign(valid(), anotherKey));
        assertRejected(sign(valid().issuer("another-app"), KEY));
        assertRejected(sign(valid().audience(List.of("another-client")), KEY));
        assertRejected(sign(valid().subject("-1"), KEY));
        assertRejected(sign(valid().subject("9223372036854775808"), KEY));
        assertRejected(sign(valid().id("invalid-jti"), KEY));
        assertRejected(sign(valid().issuedAt(NOW.plusSeconds(1)).expiresAt(NOW.plusSeconds(7201)), KEY));
        assertRejected(sign(valid().expiresAt(NOW.plusSeconds(7201)), KEY));
        assertRejected(sign(JwtClaimsSet.builder().issuer("issuer").audience(List.of("client"))
                .subject("42").id(UUID.randomUUID().toString()).issuedAt(NOW), KEY));
    }

    @Test
    void jwtContainsOnlyIdentityAndSessionClaimsAndExpiresAtExactBoundary() {
        var token = service.issue(42L);
        var verified = service.verify(token.value());
        assertThat(verified.accountId()).isEqualTo(42);
        assertThat(verified.expiresAt()).isEqualTo(NOW.plusSeconds(7200));
        var atBoundary = new JwtService(properties(KEY), Clock.fixed(NOW.plusSeconds(7200), ZoneOffset.UTC));
        assertThatThrownBy(() -> atBoundary.verify(token.value())).isInstanceOf(BusinessException.class);
        var justBefore = new JwtService(properties(KEY), Clock.fixed(NOW.plusSeconds(7199), ZoneOffset.UTC));
        assertThat(justBefore.verify(token.value()).jti()).isEqualTo(verified.jti());
    }

    @Test
    void missingOrShortKeysFailConfigurationWithoutPrintingSecrets() {
        assertThatThrownBy(() -> new JwtService(properties(new byte[31]), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JWT_SECRET_BASE64");
        assertThatThrownBy(() -> new JwtService(properties(new byte[0]), Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void assertRejected(String token) {
        assertThatThrownBy(() -> service.verify(token)).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getCode()).isEqualTo("UNAUTHENTICATED"));
    }

    private JwtClaimsSet.Builder valid() {
        return JwtClaimsSet.builder().issuer("issuer").audience(List.of("client")).subject("42")
                .id(UUID.randomUUID().toString()).issuedAt(NOW).expiresAt(NOW.plusSeconds(7200));
    }

    private String sign(JwtClaimsSet.Builder claims, byte[] key) {
        var encoder = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key, "HmacSHA256")));
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims.build())).getTokenValue();
    }

    private static AuthProperties properties(byte[] key) {
        return new AuthProperties(Base64.getEncoder().encodeToString(key), "issuer", "client",
                "http://127.0.0.1:8080", 5, 30, 10);
    }
}
