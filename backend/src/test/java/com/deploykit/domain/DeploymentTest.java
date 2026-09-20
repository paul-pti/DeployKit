package com.deploykit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeploymentTest {

    private static final Instant T1 = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-09-20T10:01:00Z");

    private Deployment pending() {
        return new Deployment(UUID.randomUUID(), 1, "ghcr.io/acme/app:main", "abc1234");
    }

    @Test
    void newDeploymentIsPending() {
        Deployment deployment = pending();

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.PENDING);
        assertThat(deployment.getStartedAt()).isNull();
        assertThat(deployment.getFinishedAt()).isNull();
    }

    @Test
    void successfulLifecycle() {
        Deployment deployment = pending();

        deployment.markDeploying(T1);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.DEPLOYING);
        assertThat(deployment.getStartedAt()).isEqualTo(T1);

        deployment.markRunning(T2);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
        assertThat(deployment.getFinishedAt()).isEqualTo(T2);
    }

    @Test
    void canFailFromPendingOrDeploying() {
        Deployment fromPending = pending();
        fromPending.markFailed("queue full", T1);
        assertThat(fromPending.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(fromPending.getErrorMessage()).isEqualTo("queue full");

        Deployment fromDeploying = pending();
        fromDeploying.markDeploying(T1);
        fromDeploying.markFailed("helm failed", T2);
        assertThat(fromDeploying.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(fromDeploying.getFinishedAt()).isEqualTo(T2);
    }

    @Test
    void rejectsInvalidTransitions() {
        Deployment finished = pending();
        finished.markDeploying(T1);
        finished.markRunning(T2);

        assertThatThrownBy(() -> finished.markFailed("late", T2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> finished.markDeploying(T2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> pending().markRunning(T1)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlyInFlightStatusesAreActive() {
        assertThat(DeploymentStatus.ACTIVE)
                .containsExactlyInAnyOrder(DeploymentStatus.PENDING, DeploymentStatus.BUILDING, DeploymentStatus.DEPLOYING);
        assertThat(DeploymentStatus.RUNNING.isActive()).isFalse();
        assertThat(DeploymentStatus.FAILED.isActive()).isFalse();
        assertThat(DeploymentStatus.ROLLED_BACK.isActive()).isFalse();
    }
}
