package com.deploykit.dto;

import com.deploykit.domain.DeploymentStatus;
import java.time.Instant;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        UUID projectId,
        int version,
        DeploymentStatus status,
        String image,
        String commitSha,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        String errorMessage) {
}
