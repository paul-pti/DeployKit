package com.deploykit.exception;

/** Too many failed logins: try again after {@code retryAfterSeconds} (HTTP 429). */
public class TooManyAttemptsException extends RuntimeException {

    private final int retryAfterSeconds;

    public TooManyAttemptsException(int retryAfterSeconds) {
        super("Too many failed login attempts, try again later");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
