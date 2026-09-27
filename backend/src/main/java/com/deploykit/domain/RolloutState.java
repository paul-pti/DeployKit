package com.deploykit.domain;

/** Rollout state of a Kubernetes Deployment, as computed by KubernetesService. */
public enum RolloutState {
    PROGRESSING,
    AVAILABLE,
    FAILED
}
