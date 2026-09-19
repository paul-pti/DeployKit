package com.deploykit.exception;

/** A Helm command failed, timed out or could not be started. */
public class HelmOperationException extends RuntimeException {

    private final String output;

    public HelmOperationException(String message, String output, Throwable cause) {
        super(message, cause);
        this.output = output == null ? "" : output;
    }

    public HelmOperationException(String message, String output) {
        this(message, output, null);
    }

    /** Combined stdout/stderr of the failed command, for the deployment log. */
    public String getOutput() {
        return output;
    }
}
