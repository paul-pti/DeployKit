package com.deploykit.exception;

/**
 * The logs of a pod cannot be read right now, for a reason that is normal rather than a failure of the cluster:
 * typically the container has not started yet (image still being pulled, or it cannot be pulled).
 */
public class PodLogsUnavailableException extends RuntimeException {

    public PodLogsUnavailableException(String message) {
        super(message);
    }
}
