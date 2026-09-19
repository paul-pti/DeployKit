package com.deploykit.dto;

import com.deploykit.domain.RolloutState;

public record DeploymentStatusInfo(
        String namespace,
        String name,
        RolloutState state,
        int desiredReplicas,
        int readyReplicas,
        int updatedReplicas,
        int availableReplicas,
        String message) {
}
