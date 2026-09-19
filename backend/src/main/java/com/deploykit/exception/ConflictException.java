package com.deploykit.exception;

/** The request conflicts with the current state of a resource (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
