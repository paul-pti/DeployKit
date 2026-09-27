package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentLog;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.LogLevel;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.observability.DeploymentMetrics;
import com.deploykit.repository.DeploymentLogRepository;
import com.deploykit.repository.DeploymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class DeploymentRecorderTest {

    private final UUID projectId = UUID.randomUUID();
    private final UUID deploymentId = UUID.randomUUID();

    private DeploymentRepository deployments;
    private DeploymentLogRepository logs;
    private SimpleMeterRegistry meterRegistry;
    private DeploymentRecorder recorder;

    @BeforeEach
    void setUp() {
        deployments = mock(DeploymentRepository.class);
        logs = mock(DeploymentLogRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        recorder = new DeploymentRecorder(deployments, logs, new DeploymentMetrics(meterRegistry, deployments));
        when(deployments.saveAndFlush(any(Deployment.class))).thenAnswer(invocation -> {
            Deployment deployment = invocation.getArgument(0);
            ReflectionTestUtils.setField(deployment, "id", deploymentId);
            return deployment;
        });
    }

    private Deployment stored(int version) {
        Deployment deployment = new Deployment(projectId, version, "ghcr.io/acme/app:main", null);
        ReflectionTestUtils.setField(deployment, "id", deploymentId);
        when(deployments.findById(deploymentId)).thenReturn(Optional.of(deployment));
        return deployment;
    }

    @Test
    void firstDeploymentOfAProjectIsVersionOne() {
        when(deployments.currentVersion(projectId)).thenReturn(0);

        Deployment created = recorder.createPending(projectId, "ghcr.io/acme/app:main", null);

        assertThat(created.getVersion()).isEqualTo(1);
        assertThat(created.getStatus()).isEqualTo(DeploymentStatus.PENDING);
    }

    @Test
    void versionsContinueFromTheLatestOne() {
        when(deployments.currentVersion(projectId)).thenReturn(4);

        assertThat(recorder.createPending(projectId, "img:1", "abc1234").getVersion()).isEqualTo(5);
    }

    @Test
    void createPendingStoresAQueuedLogEntry() {
        when(deployments.currentVersion(projectId)).thenReturn(0);

        recorder.createPending(projectId, "ghcr.io/acme/app:main", null);

        ArgumentCaptor<DeploymentLog> log = ArgumentCaptor.forClass(DeploymentLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().getDeploymentId()).isEqualTo(deploymentId);
        assertThat(log.getValue().getLevel()).isEqualTo(LogLevel.INFO);
        assertThat(log.getValue().getMessage()).contains("queued").contains("ghcr.io/acme/app:main");
    }

    @Test
    void refusesToStartWhileAnotherDeploymentIsInFlight() {
        when(deployments.existsByProjectIdAndStatusIn(projectId, DeploymentStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> recorder.createPending(projectId, "img:1", null))
                .isInstanceOf(ConflictException.class);
        verify(deployments, never()).saveAndFlush(any());
    }

    @Test
    void transitionsAreRecordedWithTimestampsAndLogs() {
        Deployment deployment = stored(1);

        recorder.markDeploying(deploymentId);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.DEPLOYING);
        assertThat(deployment.getStartedAt()).isNotNull();

        recorder.markRunning(deploymentId);
        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
        assertThat(deployment.getFinishedAt()).isNotNull();
        verify(logs).save(any(DeploymentLog.class));
        assertThat(meterRegistry.get("deploykit.deployments.total")
                .tag("status", "RUNNING").tag("rollback", "false").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("deploykit.deployment.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void failureStoresTheReasonAndAnErrorLog() {
        Deployment deployment = stored(1);

        recorder.markFailed(deploymentId, "Pod x is ImagePullBackOff");

        assertThat(deployment.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(deployment.getErrorMessage()).isEqualTo("Pod x is ImagePullBackOff");
        ArgumentCaptor<DeploymentLog> log = ArgumentCaptor.forClass(DeploymentLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().getLevel()).isEqualTo(LogLevel.ERROR);
        assertThat(meterRegistry.get("deploykit.deployments.total")
                .tag("status", "FAILED").tag("rollback", "false").counter().count()).isEqualTo(1);
    }

    @Test
    void oversizedErrorsAndLogsAreTruncated() {
        Deployment deployment = stored(1);

        recorder.markFailed(deploymentId, "x".repeat(10_000));

        assertThat(deployment.getErrorMessage()).hasSizeLessThan(2_100).endsWith("(truncated)");
        ArgumentCaptor<DeploymentLog> log = ArgumentCaptor.forClass(DeploymentLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().getMessage()).hasSizeLessThan(8_100).endsWith("(truncated)");
    }

    @Test
    void listWithoutStatusesReturnsEveryDeploymentOfTheProject() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Deployment> all = new PageImpl<>(List.of());
        when(deployments.findByProjectId(projectId, pageable)).thenReturn(all);

        assertThat(recorder.list(projectId, null, pageable)).isSameAs(all);
        assertThat(recorder.list(projectId, Set.of(), pageable)).isSameAs(all);
    }

    @Test
    void listWithStatusesFiltersOnThem() {
        Pageable pageable = PageRequest.of(0, 20);
        Set<DeploymentStatus> failed = Set.of(DeploymentStatus.FAILED);
        Page<Deployment> failures = new PageImpl<>(List.of());
        when(deployments.findByProjectIdAndStatusIn(projectId, failed, pageable)).thenReturn(failures);

        assertThat(recorder.list(projectId, failed, pageable)).isSameAs(failures);
        verify(deployments, never()).findByProjectId(any(), any());
    }

    private Deployment storedRollback(int version, int rollbackOf) {
        Deployment deployment = new Deployment(projectId, version, "nginx:1.26-alpine", "abc1234", rollbackOf);
        ReflectionTestUtils.setField(deployment, "id", deploymentId);
        when(deployments.findById(deploymentId)).thenReturn(Optional.of(deployment));
        return deployment;
    }

    private Deployment runningVersion(int version) {
        Deployment deployment = new Deployment(projectId, version, "img:" + version, null);
        ReflectionTestUtils.setField(deployment, "id", UUID.randomUUID());
        deployment.markDeploying(Instant.now());
        deployment.markRunning(Instant.now());
        return deployment;
    }

    @Test
    void aRollbackRecordsTheVersionItRestores() {
        when(deployments.currentVersion(projectId)).thenReturn(4);

        Deployment created = recorder.createPending(projectId, "nginx:1.26-alpine", "abc1234", 2);

        assertThat(created.getVersion()).isEqualTo(5);
        assertThat(created.getRollbackOfVersion()).isEqualTo(2);
        ArgumentCaptor<DeploymentLog> log = ArgumentCaptor.forClass(DeploymentLog.class);
        verify(logs).save(log.capture());
        assertThat(log.getValue().getMessage()).contains("Rollback to version 2").contains("nginx:1.26-alpine");
    }

    @Test
    void aRollbackMarksTheRunningVersionsItReplacedAsRolledBack() {
        Deployment rollback = storedRollback(5, 2);
        rollback.markDeploying(Instant.now());
        Deployment third = runningVersion(3);
        Deployment fourth = runningVersion(4);
        when(deployments.findWithStatusBetweenVersions(projectId, DeploymentStatus.RUNNING, 2, 5))
                .thenReturn(List.of(third, fourth));

        recorder.markRunning(deploymentId);

        assertThat(rollback.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
        assertThat(third.getStatus()).isEqualTo(DeploymentStatus.ROLLED_BACK);
        assertThat(fourth.getStatus()).isEqualTo(DeploymentStatus.ROLLED_BACK);
        // "Deployment is running" for the rollback, plus one note on each replaced version.
        verify(logs, times(3)).save(any(DeploymentLog.class));
        assertThat(meterRegistry.get("deploykit.deployments.total")
                .tag("status", "RUNNING").tag("rollback", "true").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("deploykit.deployments.total")
                .tag("status", "ROLLED_BACK").counter().count()).isEqualTo(2);
    }

    @Test
    void aRegularDeploymentDoesNotTouchOtherVersionsWhenItStartsRunning() {
        Deployment deployment = stored(3);
        deployment.markDeploying(Instant.now());

        recorder.markRunning(deploymentId);

        verify(deployments, never()).findWithStatusBetweenVersions(any(), any(), anyInt(), anyInt());
    }

    @Test
    void theRollbackLookupsDelegateToTheRepository() {
        Deployment second = stored(2);
        when(deployments.findFirstByProjectIdOrderByVersionDesc(projectId)).thenReturn(Optional.of(second));
        when(deployments.findByProjectIdAndVersion(projectId, 2)).thenReturn(Optional.of(second));
        when(deployments.findFirstByProjectIdAndVersionLessThanAndStatusInAndImageNotOrderByVersionDesc(
                projectId, 3, DeploymentStatus.SUCCEEDED, "bad:1")).thenReturn(Optional.of(second));

        assertThat(recorder.latest(projectId)).contains(second);
        assertThat(recorder.findVersion(projectId, 2)).contains(second);
        assertThat(recorder.previousSuccessful(projectId, 3, "bad:1")).contains(second);
    }

    @Test
    void getThrowsWhenTheDeploymentDoesNotExist() {
        when(deployments.findById(deploymentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> recorder.get(deploymentId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void interruptedDeploymentsAreFailedOnStartup() {
        Deployment stale = stored(3);
        when(deployments.findAllByStatusIn(DeploymentStatus.ACTIVE)).thenReturn(List.of(stale));

        int failed = recorder.failInterrupted();

        assertThat(failed).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(DeploymentStatus.FAILED);
        assertThat(stale.getErrorMessage()).contains("backend restart");
    }
}
