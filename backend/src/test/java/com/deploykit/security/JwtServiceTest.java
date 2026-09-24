package com.deploykit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deploykit.domain.Role;
import com.deploykit.domain.User;
import com.deploykit.support.TestUsers;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.test.util.ReflectionTestUtils;

class JwtServiceTest {

    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String OTHER_SECRET = "another-secret-another-secret-0123456789ab";

    private final SecurityConfiguration configuration = new SecurityConfiguration();

    private static SecurityProperties properties(String secret, String issuer) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(secret, issuer, TTL), new SecurityProperties.BootstrapAdmin(null, null));
    }

    private JwtService service(SecurityProperties properties, Clock clock) {
        SecretKey key = configuration.jwtSigningKey(properties);
        return new JwtService(configuration.jwtEncoder(key), properties, clock);
    }

    private JwtDecoder decoder(SecurityProperties properties) {
        return configuration.jwtDecoder(configuration.jwtSigningKey(properties), properties);
    }

    private static User user(Role role) {
        User user = new User("alice@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", TestUsers.USER_ID);
        return user;
    }

    @Test
    void anIssuedTokenCarriesTheIdentityAndExpiry() {
        SecurityProperties properties = properties(TestUsers.JWT_SECRET, "deploykit");

        JwtService.IssuedToken token = service(properties, Clock.systemUTC()).issue(user(Role.ADMIN));
        Jwt jwt = decoder(properties).decode(token.value());

        assertThat(jwt.getSubject()).isEqualTo(TestUsers.USER_ID.toString());
        assertThat(jwt.getClaimAsString(CurrentUser.ROLE_CLAIM)).isEqualTo("ADMIN");
        assertThat(jwt.getClaimAsString(CurrentUser.EMAIL_CLAIM)).isEqualTo("alice@example.com");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("deploykit");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(TTL);
        assertThat(token.expiresInSeconds()).isEqualTo(TTL.toSeconds());
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        String forged = service(properties(OTHER_SECRET, "deploykit"), Clock.systemUTC()).issue(user(Role.ADMIN)).value();

        assertThatThrownBy(() -> decoder(properties(TestUsers.JWT_SECRET, "deploykit")).decode(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void anExpiredTokenIsRejected() {
        Clock longAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(3)), java.time.ZoneOffset.UTC);
        SecurityProperties properties = properties(TestUsers.JWT_SECRET, "deploykit");
        String expired = service(properties, longAgo).issue(user(Role.USER)).value();

        assertThatThrownBy(() -> decoder(properties).decode(expired))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() {
        String foreign = service(properties(TestUsers.JWT_SECRET, "someone-else"), Clock.systemUTC())
                .issue(user(Role.USER)).value();

        assertThatThrownBy(() -> decoder(properties(TestUsers.JWT_SECRET, "deploykit")).decode(foreign))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void aTamperedPayloadIsRejected() {
        SecurityProperties properties = properties(TestUsers.JWT_SECRET, "deploykit");
        String token = service(properties, Clock.systemUTC()).issue(user(Role.USER)).value();
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"USER\"", "\"ADMIN\"");
        String tampered = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertThatThrownBy(() -> decoder(properties).decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void anUnsignedTokenIsRejected() {
        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String header = encoder.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encoder.encodeToString(
                ("{\"sub\":\"" + UUID.randomUUID() + "\",\"role\":\"ADMIN\",\"iss\":\"deploykit\"}")
                        .getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> decoder(properties(TestUsers.JWT_SECRET, "deploykit")).decode(header + "." + payload + "."))
                .isInstanceOf(JwtException.class);
    }
}
