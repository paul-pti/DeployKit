package com.deploykit.exception;

/** The service cannot take more work right now (HTTP 503). */
public class ServiceBusyException extends RuntimeException {

    public ServiceBusyException(String message) {
        super(message);
    }
}
