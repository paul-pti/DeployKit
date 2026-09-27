package com.deploykit.dto;

import java.time.Instant;

/**
 * @param reason waiting/terminated reason of the first unhealthy container (e.g. ImagePullBackOff,
 *               CrashLoopBackOff), or null when healthy
 * @param image  image of the pod's first container, used to tell which deployment a pod belongs to
 */
public record PodInfo(
        String name,
        String phase,
        boolean ready,
        int restarts,
        String reason,
        String image,
        Instant startedAt) {
}
