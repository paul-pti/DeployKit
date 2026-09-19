package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.ImageReference;
import com.deploykit.domain.Project;
import com.deploykit.dto.DeployRequest;
import com.deploykit.dto.DeploymentResponse;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.InvalidRequestException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.exception.ServiceBusyException;
import com.deploykit.mapper.DeploymentMapper;
import com.deploykit.repository.ProjectRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeploymentServiceTest {

    private final UUID projectId = UUID.randomUUID();
    private final UUID deploymentId = UUID.randomUUID();
    private final ImageReference image = new ImageReference("ghcr.io/acme/app", "main");

    private ProjectRepository projectRepository;
    private ImageResolver imageResolver;
    private DeploymentRecorder recorder;
    private DeploymentRunner runner;
    private List<Runnable> queued;
    private Executor executor;
    private DeploymentService service;
    private Project project;

    @BeforeEach
    void setUp() {
        projectRepository = mock(ProjectRepository.class);
        imageResolver = mock(ImageResolver.class);
        recorder = mock(DeploymentRecorder.class);
        runner = mock(DeploymentRunner.class);
        queued = new ArrayList<>();
        executor = queued::add;
        service = new DeploymentService(projectRepository, imageResolver, recorder, runner, new DeploymentMapper(), executor);

        project = new Project("demo", "https://github.com/acme/app", "main", 8080);
        ReflectionTestUtils.setField(project, "id", projectId);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(imageResolver.resolve(any(), any())).thenReturn(image);
        when(recorder.createPending(any(), anyString(), any())).thenAnswer(invocation -> {
            Deployment deployment = new Deployment(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            ReflectionTestUtils.setField(deployment, "id", deploymentId);
            return deployment;
        });
    }

    @Test
    void deployRecordsAPendingDeploymentAndQueuesTheWork() {
        DeploymentResponse response = service.deploy(projectId, null);

        assertThat(response.id()).isEqualTo(deploymentId);
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.status()).isEqualTo(DeploymentStatus.PENDING);
        assertThat(response.image()).isEqualTo("ghcr.io/acme/app:main");
        // The workflow has been handed to the executor, not run in the caller's thread.
        assertThat(queued).hasSize(1);
        verifyNoInteractions(runner);

        queued.get(0).run();
        verify(runner).run(deploymentId);
    }

    @Test
    void storesTheCommitShaLowercased() {
        service.deploy(projectId, new DeployRequest(null, "ABCDEF1234"));

        verify(recorder).createPending(projectId, "ghcr.io/acme/app:main", "abcdef1234");
    }

    @Test
    void unknownProjectIsNotFound() {
        UUID other = UUID.randomUUID();
        when(projectRepository.findById(other)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deploy(other, null)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(recorder);
        assertThat(queued).isEmpty();
    }

    @Test
    void invalidImageIsRejectedBeforeAnythingIsRecorded() {
        when(imageResolver.resolve(any(), any())).thenThrow(new InvalidRequestException("bad image"));

        assertThatThrownBy(() -> service.deploy(projectId, new DeployRequest("bad", null)))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(recorder);
    }

    @Test
    void aDeploymentAlreadyInProgressIsAConflictAndNothingIsQueued() {
        when(recorder.createPending(any(), anyString(), any()))
                .thenThrow(new ConflictException("A deployment is already in progress for this project"));

        assertThatThrownBy(() -> service.deploy(projectId, null)).isInstanceOf(ConflictException.class);
        assertThat(queued).isEmpty();
    }

    @Test
    void aFullQueueFailsTheDeploymentAndReportsBusy() {
        Executor rejecting = command -> {
            throw new RejectedExecutionException("queue full");
        };
        DeploymentService busy = new DeploymentService(
                projectRepository, imageResolver, recorder, runner, new DeploymentMapper(), rejecting);

        assertThatThrownBy(() -> busy.deploy(projectId, null)).isInstanceOf(ServiceBusyException.class);

        verify(recorder).markFailed(deploymentId, "Deployment queue is full");
    }

    @Test
    void getReturnsTheStoredDeployment() {
        Deployment deployment = new Deployment(projectId, "ghcr.io/acme/app:main", null);
        ReflectionTestUtils.setField(deployment, "id", deploymentId);
        when(recorder.get(deploymentId)).thenReturn(deployment);

        assertThat(service.get(deploymentId).id()).isEqualTo(deploymentId);
    }

    @Test
    void startupFailsDeploymentsInterruptedByARestart() {
        service.failInterruptedDeployments();

        verify(recorder).failInterrupted();
        verify(runner, never()).run(any());
    }
}
