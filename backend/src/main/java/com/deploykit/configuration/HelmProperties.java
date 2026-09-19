package com.deploykit.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param binary    Helm executable, resolved through PATH when not absolute
 * @param chartPath directory of the generic application chart (relative to the working directory)
 * @param timeout   maximum duration of a single helm command
 */
@ConfigurationProperties(prefix = "deploykit.helm")
public record HelmProperties(
        @DefaultValue("helm") String binary,
        @DefaultValue("../helm/deploykit-app") String chartPath,
        @DefaultValue("2m") Duration timeout) {
}
