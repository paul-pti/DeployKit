package com.deploykit.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param context kubeconfig context to use (e.g. {@code kind-deploykit}); when blank the client
 *                auto-configures itself (current kubeconfig context, or in-cluster service account).
 */
@ConfigurationProperties(prefix = "deploykit.kubernetes")
public record KubernetesProperties(String context) {
}
