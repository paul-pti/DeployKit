package com.deploykit.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * @param jwt            token signing configuration
 * @param bootstrapAdmin credentials of the first administrator, created at startup only while no user exists
 */
@ConfigurationProperties(prefix = "deploykit.security")
public record SecurityProperties(@DefaultValue Jwt jwt, @DefaultValue BootstrapAdmin bootstrapAdmin) {

    /**
     * @param secret HMAC key of the tokens (at least 32 bytes), from the environment; there is deliberately no default
     * @param issuer value of the {@code iss} claim, which tokens must carry to be accepted
     * @param ttl    how long an issued token stays valid
     */
    public record Jwt(String secret, @DefaultValue("deploykit") String issuer, @DefaultValue("60m") Duration ttl) {

        public Jwt {
            if (!StringUtils.hasText(secret) || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("deploykit.security.jwt.secret (environment variable "
                        + "DEPLOYKIT_JWT_SECRET) must be set to at least 32 characters. "
                        + "Generate one with: openssl rand -base64 48");
            }
            if (ttl == null || ttl.isNegative() || ttl.isZero()) {
                throw new IllegalArgumentException("deploykit.security.jwt.ttl must be a positive duration");
            }
        }
    }

    public record BootstrapAdmin(String email, String password) {

        public boolean isConfigured() {
            return StringUtils.hasText(email) && StringUtils.hasText(password);
        }
    }
}
