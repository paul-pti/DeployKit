package com.deploykit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deploykit.exception.TooManyAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-20T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

    private void fail(String key, int times) {
        for (int i = 0; i < times; i++) {
            limiter.recordFailure(key);
        }
    }

    @Test
    void allowsAttemptsBelowTheLimit() {
        fail("alice", 4);

        assertThatCode(() -> limiter.assertAllowed("alice", 5)).doesNotThrowAnyException();
    }

    @Test
    void blocksOnceTheLimitIsReachedAndSaysWhenToRetry() {
        fail("alice", 5);

        assertThatThrownBy(() -> limiter.assertAllowed("alice", 5))
                .isInstanceOfSatisfying(TooManyAttemptsException.class, e ->
                        assertThat(e.getRetryAfterSeconds()).isBetween(1, 301));
    }

    @Test
    void allowsAgainOnceTheWindowHasPassed() {
        fail("alice", 5);
        clock.advance(LoginAttemptLimiter.WINDOW.plusSeconds(1));

        assertThatCode(() -> limiter.assertAllowed("alice", 5)).doesNotThrowAnyException();
    }

    @Test
    void failuresAgeOutOneByOne() {
        fail("alice", 3);
        clock.advance(Duration.ofMinutes(4));
        fail("alice", 2);
        assertThatThrownBy(() -> limiter.assertAllowed("alice", 5)).isInstanceOf(TooManyAttemptsException.class);

        // The first three failures expire, the last two are still recent.
        clock.advance(Duration.ofMinutes(2));

        assertThatCode(() -> limiter.assertAllowed("alice", 5)).doesNotThrowAnyException();
    }

    @Test
    void aSuccessfulLoginClearsTheFailures() {
        fail("alice", 5);

        limiter.reset("alice");

        assertThatCode(() -> limiter.assertAllowed("alice", 5)).doesNotThrowAnyException();
    }

    @Test
    void keysAreIndependent() {
        fail("alice|10.0.0.1", 5);

        assertThatCode(() -> limiter.assertAllowed("alice|10.0.0.2", 5)).doesNotThrowAnyException();
        assertThatCode(() -> limiter.assertAllowed("bob|10.0.0.1", 5)).doesNotThrowAnyException();
    }

    @Test
    void theLimitIsChosenByTheCaller() {
        fail("alice", 5);

        assertThatCode(() -> limiter.assertAllowed("alice", 30)).doesNotThrowAnyException();
        assertThatThrownBy(() -> limiter.assertAllowed("alice", 5)).isInstanceOf(TooManyAttemptsException.class);
    }
}
