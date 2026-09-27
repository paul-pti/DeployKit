package com.deploykit.exception;

/** Wrong email or password. Deliberately does not say which one, so it cannot be used to discover accounts. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
