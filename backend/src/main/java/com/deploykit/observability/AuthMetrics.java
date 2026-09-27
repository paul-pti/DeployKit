package com.deploykit.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Login attempts by outcome, exposed at {@code /actuator/prometheus}. Called only from {@code AuthService}. */
@Component
public class AuthMetrics {

    private final MeterRegistry registry;

    public AuthMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** @param result one of {@code success}, {@code failure} (wrong email or password), {@code blocked} (rate limit) */
    public void recordLoginAttempt(String result) {
        Counter.builder("deploykit.auth.login.attempts")
                .description("Login attempts by outcome")
                .tag("result", result)
                .register(registry)
                .increment();
    }
}
