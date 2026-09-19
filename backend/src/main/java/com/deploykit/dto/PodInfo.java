package com.deploykit.dto;

import java.time.Instant;

/**
 * @param reason waiting/terminated reason of the first unhealthy container (e.g. ImagePullBackOff,
 *               CrashLoopBackOff), or null when healthy
 */
public record PodInfo(
        String name,
        String phase,
        boolean ready,
        int restarts,
        String reason,
        Instant startedAt) {
}
