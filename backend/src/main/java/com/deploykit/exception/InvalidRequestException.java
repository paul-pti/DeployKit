package com.deploykit.exception;

/** The request is well-formed but cannot be honoured, e.g. an unusable image reference (HTTP 400). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
