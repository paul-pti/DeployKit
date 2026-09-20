package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
import com.deploykit.repository.DeploymentLogRepository;
import com.deploykit.repository.DeploymentRepository;
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
    private DeploymentRecorder recorder;

    @BeforeEach
    void setUp() {
        deployments = mock(DeploymentRepository.class);
        logs = mock(DeploymentLogRepository.class);
        recorder = new DeploymentRecorder(deployments, logs);
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
