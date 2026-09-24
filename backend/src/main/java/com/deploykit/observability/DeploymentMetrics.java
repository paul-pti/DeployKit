package com.deploykit.observability;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.repository.DeploymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Deployment metrics, exposed at {@code /actuator/prometheus}. Recorded here, called only from
 * {@link com.deploykit.service.DeploymentRecorder} — the single place every status transition already goes through
 * — rather than sprinkled through the deployment workflow.
 */
@Component
public class DeploymentMetrics {

    private final MeterRegistry registry;

    public DeploymentMetrics(MeterRegistry registry, DeploymentRepository repository) {
        this.registry = registry;
        // Read at scrape time rather than pushed: always reflects the current count, even after a restart.
        registry.gauge("deploykit.deployments.active", repository,
                repo -> repo.countByStatusIn(DeploymentStatus.ACTIVE));
    }

    /** A deployment reached RUNNING or FAILED: counts it and, since both timestamps are now set, times it. */
    public void recordCompletion(Deployment deployment) {
        recordStatusChange(deployment.getStatus(), deployment.getRollbackOfVersion() != null);

        Instant started = deployment.getStartedAt();
        Instant finished = deployment.getFinishedAt();
        if (started != null && finished != null) {
            Timer.builder("deploykit.deployment.duration")
                    .description("Time from DEPLOYING to a terminal state")
                    .tag("status", deployment.getStatus().name())
                    // Low cardinality (one series per status): worth the extra buckets for a p95 panel.
                    .publishPercentileHistogram()
                    .register(registry)
                    .record(Duration.between(started, finished));
        }
    }

    /** A status change with no meaningful duration of its own, e.g. a deployment turning ROLLED_BACK. */
    public void recordStatusChange(DeploymentStatus status, boolean rollback) {
        Counter.builder("deploykit.deployments.total")
                .description("Deployments by final status")
                .tag("status", status.name())
                .tag("rollback", Boolean.toString(rollback))
                .register(registry)
                .increment();
    }
}
