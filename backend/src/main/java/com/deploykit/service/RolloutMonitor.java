package com.deploykit.service;

import com.deploykit.configuration.DeploymentProperties;
import com.deploykit.domain.RolloutState;
import com.deploykit.dto.DeploymentStatusInfo;
import com.deploykit.dto.PodInfo;
import com.deploykit.exception.KubernetesOperationException;
import com.deploykit.exception.ResourceNotFoundException;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

/** Polls Kubernetes until a Deployment finishes rolling out, fails, or the timeout expires. */
@Component
public class RolloutMonitor {

    /** Pod reasons that will not fix themselves (an image that cannot be pulled, a crashing container, ...). */
    static final Set<String> FAILURE_REASONS = Set.of(
            "ImagePullBackOff", "ErrImagePull", "InvalidImageName", "CrashLoopBackOff",
            "CreateContainerConfigError", "CreateContainerError", "RunContainerError");

    private static final int MAX_CONSECUTIVE_API_ERRORS = 5;
    private static final long NOT_SET = -1;

    private final KubernetesService kubernetesService;
    private final DeploymentProperties properties;

    public RolloutMonitor(KubernetesService kubernetesService, DeploymentProperties properties) {
        this.kubernetesService = kubernetesService;
        this.properties = properties;
    }

    /**
     * @param progress receives a line whenever the rollout progress changes
     */
    public RolloutResult await(String namespace, String app, Consumer<String> progress) throws InterruptedException {
        long start = System.nanoTime();
        long unhealthySince = NOT_SET;
        String lastProgress = null;
        DeploymentStatusInfo lastStatus = null;
        int consecutiveErrors = 0;

        while (true) {
            try {
                DeploymentStatusInfo status = fetchStatus(namespace, app);
                consecutiveErrors = 0;

                if (status != null) {
                    lastStatus = status;
                    String line = "Rollout: %d/%d ready, %d updated".formatted(
                            status.readyReplicas(), status.desiredReplicas(), status.updatedReplicas());
                    if (!line.equals(lastProgress)) {
                        progress.accept(line);
                        lastProgress = line;
                    }
                    if (status.state() == RolloutState.AVAILABLE) {
                        return RolloutResult.ok();
                    }
                    if (status.state() == RolloutState.FAILED) {
                        return RolloutResult.failed("Rollout failed: " + status.message());
                    }

                    PodInfo unhealthy = firstUnhealthyPod(namespace, app);
                    if (unhealthy == null) {
                        unhealthySince = NOT_SET;
                    } else {
                        String reason = "Pod %s is %s".formatted(unhealthy.name(), unhealthy.reason());
                        if (unhealthySince == NOT_SET) {
                            unhealthySince = System.nanoTime();
                            progress.accept(reason);
                        } else if (System.nanoTime() - unhealthySince >= properties.failureGrace().toNanos()) {
                            return RolloutResult.failed(reason);
                        }
                    }
                }
            } catch (KubernetesOperationException e) {
                if (++consecutiveErrors >= MAX_CONSECUTIVE_API_ERRORS) {
                    return RolloutResult.failed("Lost contact with Kubernetes: " + e.getMessage());
                }
            }

            if (System.nanoTime() - start >= properties.rolloutTimeout().toNanos()) {
                String detail = lastStatus == null ? "" : " (%d/%d ready)".formatted(
                        lastStatus.readyReplicas(), lastStatus.desiredReplicas());
                return RolloutResult.failed("Timed out after " + properties.rolloutTimeout() + " waiting for rollout" + detail);
            }
            Thread.sleep(properties.pollInterval().toMillis());
        }
    }

    /** The Deployment may not be visible yet right after Helm returns; treat that as "not ready", not as an error. */
    private DeploymentStatusInfo fetchStatus(String namespace, String app) {
        try {
            return kubernetesService.getDeploymentStatus(namespace, app);
        } catch (ResourceNotFoundException e) {
            return null;
        }
    }

    private PodInfo firstUnhealthyPod(String namespace, String app) {
        return kubernetesService.getPods(namespace, app).stream()
                .filter(pod -> pod.reason() != null && FAILURE_REASONS.contains(pod.reason()))
                .findFirst()
                .orElse(null);
    }
}
