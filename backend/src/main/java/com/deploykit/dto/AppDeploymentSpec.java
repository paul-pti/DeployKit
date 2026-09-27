package com.deploykit.dto;

import java.util.Map;
import java.util.regex.Pattern;

/** Desired state of an application's Kubernetes Deployment. */
public record AppDeploymentSpec(
        String namespace,
        String name,
        String image,
        int port,
        int replicas,
        Map<String, String> env) {

    private static final Pattern DNS_LABEL = Pattern.compile("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$");

    public AppDeploymentSpec {
        requireDnsLabel("namespace", namespace);
        requireDnsLabel("name", name);
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("image must not be blank");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        if (replicas < 0) {
            throw new IllegalArgumentException("replicas must not be negative");
        }
        env = env == null ? Map.of() : Map.copyOf(env);
    }

    private static void requireDnsLabel(String field, String value) {
        if (value == null || value.length() > 63 || !DNS_LABEL.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a valid DNS-1123 label (lowercase, digits, '-', max 63)");
        }
    }
}
