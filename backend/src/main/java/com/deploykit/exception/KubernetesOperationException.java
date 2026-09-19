package com.deploykit.exception;

/** A call to the Kubernetes API failed (cluster unreachable, forbidden, invalid object, ...). */
public class KubernetesOperationException extends RuntimeException {

    public KubernetesOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
