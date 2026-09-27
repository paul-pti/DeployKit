package com.deploykit.dto;

import com.deploykit.domain.LogLevel;
import java.time.Instant;

/** One step of the deployment workflow (queued, helm output, rollout progress, failure, ...). */
public record DeploymentEvent(Instant timestamp, LogLevel level, String message) {
}
