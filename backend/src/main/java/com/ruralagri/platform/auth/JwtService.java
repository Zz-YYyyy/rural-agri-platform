package com.ruralagri.platform.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public class JwtService {
    public static final long LIFETIME_SECONDS = 7200;
    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(properties.jwtSecretBase64());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must contain at least 32 random bytes in Base64");
        }
        if (bytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET_BASE64 must contain at least 32 random bytes in Base64");
        }
        var key = new SecretKeySpec(bytes, "HmacSHA256");
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamp = new JwtTimestampValidator(Duration.ZERO);
        timestamp.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp,
                new JwtIssuerValidator(properties.issuer()), this::validateClaims));
    }

    public IssuedToken issue(long accountId) {
        Instant issued = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expires = issued.plusSeconds(LIFETIME_SECONDS);
        String jti = UUID.randomUUID().toString();
        var claims = JwtClaimsSet.builder().issuer(properties.issuer())
                .audience(List.of(properties.audience())).subject(Long.toString(accountId))
                .issuedAt(issued).expiresAt(expires).id(jti).build();
        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
        return new IssuedToken(value, jti, expires);
    }

    public VerifiedToken verify(String value) {
        try {
            Jwt jwt = decoder.decode(value);
            return new VerifiedToken(Long.parseLong(jwt.getSubject()), jwt.getId(), jwt.getExpiresAt());
        } catch (JwtException | IllegalArgumentException exception) {
            throw AuthErrors.unauthenticated();
        }
    }

    private OAuth2TokenValidatorResult validateClaims(Jwt jwt) {
        try {
            Instant now = clock.instant();
            boolean valid = jwt.getAudience().contains(properties.audience())
                    && jwt.getSubject() != null && jwt.getSubject().matches("[1-9][0-9]{0,18}")
                    && Long.parseLong(jwt.getSubject()) > 0
                    && jwt.getId() != null && UUID.fromString(jwt.getId()).toString().equals(jwt.getId())
                    && jwt.getIssuedAt() != null && !jwt.getIssuedAt().isAfter(now)
                    && jwt.getExpiresAt() != null && now.isBefore(jwt.getExpiresAt())
                    && jwt.getExpiresAt().equals(jwt.getIssuedAt().plusSeconds(LIFETIME_SECONDS));
            if (valid) {
                return OAuth2TokenValidatorResult.success();
            }
        } catch (RuntimeException ignored) {
            // Malformed claims never become an authenticated principal.
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
    }

    public record IssuedToken(String value, String jti, Instant expiresAt) {
        @Override
        public String toString() {
            return "IssuedToken[REDACTED]";
        }
    }

    public record VerifiedToken(long accountId, String jti, Instant expiresAt) {
    }
}
