package com.deploykit.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String name,
        String repositoryUrl,
        String branch,
        int port,
        Instant createdAt,
        Instant updatedAt) {
}
