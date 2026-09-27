package com.deploykit.dto;

/**
 * Logs of one pod of the application.
 *
 * @param reason waiting/terminated reason of the container (ImagePullBackOff, CrashLoopBackOff, ...), or null
 * @param log    the last lines of the container output; empty when {@code error} is set
 * @param error  why the logs could not be read (for example "container is waiting to start"), or null
 */
public record PodLogs(
        String pod,
        String phase,
        boolean ready,
        int restarts,
        String reason,
        String log,
        String error) {
}
