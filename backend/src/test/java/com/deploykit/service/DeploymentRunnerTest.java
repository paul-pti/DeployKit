package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.Environment;
import com.deploykit.domain.ImageReference;
import com.deploykit.domain.LogLevel;
import com.deploykit.domain.Project;
import com.deploykit.dto.HelmRelease;
import com.deploykit.exception.HelmOperationException;
import com.deploykit.exception.KubernetesOperationException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.repository.ProjectRepository;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class DeploymentRunnerTest {

    private final UUID projectId = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000000");
    private final UUID deploymentId = UUID.randomUUID();
    private final String app = "demo-a1b2c3";
    private final String namespace = "dk-demo-a1b2c3";

    private DeploymentRecorder recorder;
    private ProjectRepository projectRepository;
    private EnvironmentService environmentService;
    private KubernetesService kubernetesService;
    private HelmService helmService;
    private RolloutMonitor rolloutMonitor;
    private DeploymentRunner runner;
    private Project project;

    @BeforeEach
    void setUp() throws Exception {
        recorder = mock(DeploymentRecorder.class);
        projectRepository = mock(ProjectRepository.class);
        environmentService = mock(EnvironmentService.class);
        kubernetesService = mock(KubernetesService.class);
        helmService = mock(HelmService.class);
        rolloutMonitor = mock(RolloutMonitor.class);
        runner = new DeploymentRunner(recorder, projectRepository, environmentService, kubernetesService,
                helmService, rolloutMonitor);

        project = new Project("demo", "https://github.com/acme/app", "main", 8080);
        ReflectionTestUtils.setField(project, "id", projectId);
        Deployment deployment = new Deployment(projectId, 1, "nginx:1.27-alpine", null);
        ReflectionTestUtils.setField(deployment, "id", deploymentId);

        when(recorder.get(deploymentId)).thenReturn(deployment);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(environmentService.getOrCreateDefault(project)).thenReturn(new Environment(projectId, "default", namespace));
        when(helmService.upgradeInstall(any())).thenReturn("STATUS: deployed\n");
        when(rolloutMonitor.await(eq(namespace), eq(app), any())).thenReturn(RolloutResult.ok());
    }

    @Test
    void successfulDeploymentEndsRunning() throws Exception {
        runner.run(deploymentId);

        var order = inOrder(recorder, kubernetesService, helmService, rolloutMonitor);
        order.verify(recorder).markDeploying(deploymentId);
        order.verify(kubernetesService).ensureNamespace(namespace);
        order.verify(helmService).upgradeInstall(any());
        order.verify(rolloutMonitor).await(eq(namespace), eq(app), any());
        order.verify(recorder).markRunning(deploymentId);
        verify(recorder, never()).markFailed(any(), anyString());
    }

    @Test
    void passesTheRightReleaseToHelm() {
        runner.run(deploymentId);

        ArgumentCaptor<HelmRelease> release = ArgumentCaptor.forClass(HelmRelease.class);
        verify(helmService).upgradeInstall(release.capture());
        assertThat(release.getValue()).isEqualTo(
                new HelmRelease(app, namespace, ImageReference.parse("nginx:1.27-alpine"), 8080));
    }

    @Test
    void rolloutProgressIsStoredInTheDeploymentLog() throws Exception {
        when(rolloutMonitor.await(eq(namespace), eq(app), any())).thenAnswer(invocation -> {
            Consumer<String> progress = invocation.getArgument(2);
            progress.accept("Rollout: 1/1 ready, 1 updated");
            return RolloutResult.ok();
        });

        runner.run(deploymentId);

        verify(recorder).log(deploymentId, LogLevel.INFO, "Rollout: 1/1 ready, 1 updated");
    }

    @Test
    void helmFailureFailsTheDeploymentAndStoresHelmOutput() {
        when(helmService.upgradeInstall(any()))
                .thenThrow(new HelmOperationException("helm exited with code 1: boom", "Error: boom"));

        runner.run(deploymentId);

        verify(recorder).log(eq(deploymentId), eq(LogLevel.ERROR), contains("Error: boom"));
        verify(recorder).markFailed(deploymentId, "helm exited with code 1: boom");
        verify(recorder, never()).markRunning(any());
        verifyNoInteractions(rolloutMonitor);
    }

    @Test
    void failedRolloutFailsTheDeploymentWithTheReason() throws Exception {
        when(rolloutMonitor.await(eq(namespace), eq(app), any()))
                .thenReturn(RolloutResult.failed("Pod demo-pod is ImagePullBackOff"));

        runner.run(deploymentId);

        verify(recorder).markFailed(deploymentId, "Pod demo-pod is ImagePullBackOff");
        verify(recorder, never()).markRunning(any());
    }

    @Test
    void missingProjectFailsTheDeployment() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        runner.run(deploymentId);

        verify(recorder).markFailed(eq(deploymentId), startsWith("Unexpected error: Project no longer exists"));
        verifyNoInteractions(helmService);
    }

    @Test
    void unexpectedErrorsFailTheDeploymentInsteadOfEscaping() {
        doThrow(new KubernetesOperationException("cluster down", new RuntimeException()))
                .when(kubernetesService).ensureNamespace(namespace);

        assertThatCode(() -> runner.run(deploymentId)).doesNotThrowAnyException();

        verify(recorder).markFailed(eq(deploymentId), startsWith("Unexpected error: cluster down"));
        verifyNoInteractions(helmService);
    }

    @Test
    void aFailureToRecordTheFailureDoesNotEscapeEither() {
        when(helmService.upgradeInstall(any())).thenThrow(new HelmOperationException("boom", ""));
        doThrow(new IllegalStateException("db down")).when(recorder).markFailed(any(), anyString());

        assertThatCode(() -> runner.run(deploymentId)).doesNotThrowAnyException();
    }

    @Test
    void aDeploymentThatNoLongerExistsIsIgnored() {
        when(recorder.get(deploymentId)).thenThrow(new ResourceNotFoundException("gone"));

        assertThatCode(() -> runner.run(deploymentId)).doesNotThrowAnyException();

        verifyNoInteractions(helmService, kubernetesService, rolloutMonitor);
        verify(recorder, never()).markDeploying(any());
    }
}
