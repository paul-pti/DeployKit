package com.deploykit.security;

import com.deploykit.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Slows down password guessing: a key (an email, or an email plus a client address) may fail at most a given number
 * of times per window. State is in memory, which matches the single-instance deployment model.
 */
@Component
public class LoginAttemptLimiter {

    static final Duration WINDOW = Duration.ofMinutes(5);
    private static final int PRUNE_THRESHOLD = 10_000;

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    /** @throws TooManyAttemptsException when the key already failed {@code maxFailures} times within the window */
    public void assertAllowed(String key, int maxFailures) {
        Instant now = clock.instant();
        Deque<Instant> recent = failures.get(key);
        if (recent == null) {
            return;
        }
        synchronized (recent) {
            dropExpired(recent, now);
            if (recent.size() >= maxFailures) {
                long retryAfter = Duration.between(now, recent.peekFirst().plus(WINDOW)).toSeconds() + 1;
                throw new TooManyAttemptsException(Math.max(1, (int) retryAfter));
            }
        }
    }

    public void recordFailure(String key) {
        if (failures.size() > PRUNE_THRESHOLD) {
            pruneExpired();
        }
        Deque<Instant> recent = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (recent) {
            recent.addLast(clock.instant());
        }
    }

    public void reset(String key) {
        failures.remove(key);
    }

    private void pruneExpired() {
        Instant now = clock.instant();
        failures.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                dropExpired(entry.getValue(), now);
                return entry.getValue().isEmpty();
            }
        });
    }

    private static void dropExpired(Deque<Instant> recent, Instant now) {
        while (!recent.isEmpty() && recent.peekFirst().plus(WINDOW).isBefore(now)) {
            recent.pollFirst();
        }
    }
}
