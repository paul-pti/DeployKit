package com.deploykit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deploykit.support.TestUsers;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SecurityPropertiesTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "too-short", "0123456789012345678901234567890"})
    void aMissingOrShortSecretStopsTheApplicationFromStarting(String secret) {
        assertThatThrownBy(() -> new SecurityProperties.Jwt(secret, "deploykit", Duration.ofMinutes(60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEPLOYKIT_JWT_SECRET")
                .hasMessageContaining("openssl rand");
    }

    @Test
    void aSecretOfAtLeast32BytesIsAccepted() {
        assertThatCode(() -> new SecurityProperties.Jwt(TestUsers.JWT_SECRET, "deploykit", Duration.ofMinutes(60)))
                .doesNotThrowAnyException();
    }

    @Test
    void theLifetimeMustBePositive() {
        assertThatThrownBy(() -> new SecurityProperties.Jwt(TestUsers.JWT_SECRET, "deploykit", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties.Jwt(TestUsers.JWT_SECRET, "deploykit", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theBootstrapAdministratorNeedsBothValues() {
        assertThat(new SecurityProperties.BootstrapAdmin("admin@example.com", "a-long-password").isConfigured()).isTrue();
        assertThat(new SecurityProperties.BootstrapAdmin("admin@example.com", "").isConfigured()).isFalse();
        assertThat(new SecurityProperties.BootstrapAdmin(null, "a-long-password").isConfigured()).isFalse();
        assertThat(new SecurityProperties.BootstrapAdmin(null, null).isConfigured()).isFalse();
    }
}
