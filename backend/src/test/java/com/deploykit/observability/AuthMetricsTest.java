package com.deploykit.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AuthMetricsTest {

    @Test
    void countsLoginAttemptsByOutcome() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuthMetrics metrics = new AuthMetrics(registry);

        metrics.recordLoginAttempt("success");
        metrics.recordLoginAttempt("failure");
        metrics.recordLoginAttempt("failure");
        metrics.recordLoginAttempt("blocked");

        assertThat(registry.get("deploykit.auth.login.attempts").tag("result", "success").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("deploykit.auth.login.attempts").tag("result", "failure").counter().count())
                .isEqualTo(2);
        assertThat(registry.get("deploykit.auth.login.attempts").tag("result", "blocked").counter().count())
                .isEqualTo(1);
    }
}
