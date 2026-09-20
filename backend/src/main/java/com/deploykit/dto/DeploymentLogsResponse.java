package com.deploykit.dto;

import com.deploykit.domain.DeploymentStatus;
import java.util.List;
import java.util.UUID;

/**
 * Everything worth reading about a deployment: the logs of the pods running its image, and the events of the
 * deployment workflow itself.
 *
 * @param podsNote human-readable reason why {@code pods} is empty or incomplete (the deployment has not started, its
 *                 pods were replaced by a newer deployment, Kubernetes is unreachable, ...), or null
 */
public record DeploymentLogsResponse(
        UUID deploymentId,
        DeploymentStatus status,
        List<PodLogs> pods,
        String podsNote,
        List<DeploymentEvent> events) {
}
