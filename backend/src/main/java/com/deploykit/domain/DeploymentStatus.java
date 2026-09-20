package com.deploykit.domain;

import java.util.EnumSet;
import java.util.Set;

public enum DeploymentStatus {
    PENDING,
    BUILDING,
    DEPLOYING,
    RUNNING,
    FAILED,
    ROLLED_BACK;

    /** States in which a deployment is still in flight. Mirrors the partial unique index in migration V2. */
    public static final Set<DeploymentStatus> ACTIVE = EnumSet.of(PENDING, BUILDING, DEPLOYING);

    /** States of a deployment that reached RUNNING at some point: candidates to roll back to. */
    public static final Set<DeploymentStatus> SUCCEEDED = EnumSet.of(RUNNING, ROLLED_BACK);

    public boolean isActive() {
        return ACTIVE.contains(this);
    }
}
