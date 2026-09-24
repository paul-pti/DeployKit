package com.deploykit.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.repository.DeploymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeploymentMetricsTest {

    private SimpleMeterRegistry registry;
    private DeploymentRepository repository;
    private DeploymentMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        repository = mock(DeploymentRepository.class);
        metrics = new DeploymentMetrics(registry, repository);
    }

    private static Deployment deployment(DeploymentStatus status, Integer rollbackOfVersion) {
        Deployment deployment = new Deployment(UUID.randomUUID(), 1, "nginx:1.27-alpine", null, rollbackOfVersion);
        ReflectionTestUtils.setField(deployment, "status", status);
        ReflectionTestUtils.setField(deployment, "startedAt", Instant.parse("2026-09-24T10:00:00Z"));
        ReflectionTestUtils.setField(deployment, "finishedAt", Instant.parse("2026-09-24T10:00:05Z"));
        return deployment;
    }

    @Test
    void countsAndTimesACompletedDeployment() {
        metrics.recordCompletion(deployment(DeploymentStatus.RUNNING, null));

        assertThat(registry.get("deploykit.deployments.total")
                .tag("status", "RUNNING").tag("rollback", "false").counter().count()).isEqualTo(1);
        assertThat(registry.get("deploykit.deployment.duration")
                .tag("status", "RUNNING").timer().totalTime(TimeUnit.SECONDS)).isEqualTo(5);
    }

    @Test
    void tagsAFailedDeploymentDifferently() {
        metrics.recordCompletion(deployment(DeploymentStatus.FAILED, null));

        assertThat(registry.get("deploykit.deployments.total")
                .tag("status", "FAILED").tag("rollback", "false").counter().count()).isEqualTo(1);
    }

    @Test
    void tagsARollbackDeployment() {
        metrics.recordCompletion(deployment(DeploymentStatus.RUNNING, 3));

        assertThat(registry.get("deploykit.deployments.total")
                .tag("status", "RUNNING").tag("rollback", "true").counter().count()).isEqualTo(1);
    }

    @Test
    void recordsAStatusChangeWithoutATimer() {
        metrics.recordStatusChange(DeploymentStatus.ROLLED_BACK, false);

        assertThat(registry.get("deploykit.deployments.total")
                .tag("status", "ROLLED_BACK").counter().count()).isEqualTo(1);
        assertThat(registry.find("deploykit.deployment.duration").timer()).isNull();
    }

    @Test
    void theActiveGaugeReadsTheRepositoryAtScrapeTime() {
        when(repository.countByStatusIn(DeploymentStatus.ACTIVE)).thenReturn(2L, 5L);

        assertThat(registry.get("deploykit.deployments.active").gauge().value()).isEqualTo(2);
        assertThat(registry.get("deploykit.deployments.active").gauge().value()).isEqualTo(5);
    }
}
