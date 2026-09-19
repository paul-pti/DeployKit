package com.deploykit.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param registry       container registry hosting the images built for projects
 * @param rolloutTimeout how long to wait for a rollout to become available
 * @param pollInterval   delay between two rollout status checks
 * @param failureGrace   how long a pod may report a failure reason (ImagePullBackOff, ...) before the
 *                       deployment is failed without waiting for the full timeout
 */
@ConfigurationProperties(prefix = "deploykit.deployment")
public record DeploymentProperties(
        @DefaultValue("ghcr.io") String registry,
        @DefaultValue("5m") Duration rolloutTimeout,
        @DefaultValue("2s") Duration pollInterval,
        @DefaultValue("30s") Duration failureGrace) {
}
