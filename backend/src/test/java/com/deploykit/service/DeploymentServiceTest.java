package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.ImageReference;
import com.deploykit.domain.LogLevel;
import com.deploykit.domain.Project;
import com.deploykit.dto.DeployRequest;
import com.deploykit.dto.RollbackRequest;
import com.deploykit.dto.DeploymentPage;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
        when(recorder.createPending(any(), anyString(), any(), any())).thenAnswer(invocation -> {
            Deployment deployment = new Deployment(invocation.getArgument(0), 8, invocation.getArgument(1),
                    invocation.getArgument(2), invocation.getArgument(3));
            ReflectionTestUtils.setField(deployment, "id", deploymentId);
            return deployment;
        });
        when(recorder.createPending(any(), anyString(), any())).thenAnswer(invocation -> {
            Deployment deployment = new Deployment(
                    invocation.getArgument(0), 7, invocation.getArgument(1), invocation.getArgument(2));
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
    void theCommitIsRecordedWhenTheImageIsTaggedWithACommitSha() {
        when(imageResolver.resolve(any(), any())).thenReturn(new ImageReference("ghcr.io/acme/app", "abc1234def"));

        service.deploy(projectId, null);

        verify(recorder).createPending(projectId, "ghcr.io/acme/app:abc1234def", "abc1234def");
    }

    @Test
    void noCommitIsRecordedForABranchTaggedImage() {
        service.deploy(projectId, null);

        verify(recorder).createPending(projectId, "ghcr.io/acme/app:main", null);
    }

    @Test
    void listReturnsAPageNewestFirstWithTheRequestedFilter() {
        Deployment first = new Deployment(projectId, 2, "ghcr.io/acme/app:main", null);
        ReflectionTestUtils.setField(first, "id", deploymentId);
        Pageable expected = PageRequest.of(1, 5, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("version")));
        Set<DeploymentStatus> statuses = Set.of(DeploymentStatus.FAILED);
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(recorder.list(projectId, statuses, expected)).thenReturn(new PageImpl<>(List.of(first), expected, 6));

        DeploymentPage page = service.list(projectId, statuses, 1, 5);

        assertThat(page.content()).extracting(DeploymentResponse::version).containsExactly(2);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.size()).isEqualTo(5);
        assertThat(page.totalElements()).isEqualTo(6);
        assertThat(page.totalPages()).isEqualTo(2);
    }

    @Test
    void listClampsThePageAndSizeInsteadOfTrustingTheClient() {
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(recorder.list(any(), any(), any())).thenAnswer(invocation ->
                new PageImpl<Deployment>(List.of(), invocation.getArgument(2), 0));

        DeploymentPage tooBig = service.list(projectId, null, -3, 100_000);
        DeploymentPage tooSmall = service.list(projectId, null, 0, 0);

        assertThat(tooBig.page()).isZero();
        assertThat(tooBig.size()).isEqualTo(DeploymentService.MAX_PAGE_SIZE);
        assertThat(tooSmall.size()).isEqualTo(1);
    }

    @Test
    void listOfAnUnknownProjectIsNotFound() {
        when(projectRepository.existsById(projectId)).thenReturn(false);

        assertThatThrownBy(() -> service.list(projectId, null, 0, 20)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(recorder);
    }

    private Deployment version(int version, String image, String commitSha, DeploymentStatus status) {
        Deployment deployment = new Deployment(projectId, version, image, commitSha);
        ReflectionTestUtils.setField(deployment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(deployment, "status", status);
        return deployment;
    }

    /** Version 3 of the project, failed with a bad image, and the latest deployment. */
    private Deployment latestFailure() {
        Deployment source = version(3, "ghcr.io/acme/app:bad", null, DeploymentStatus.FAILED);
        when(recorder.get(source.getId())).thenReturn(source);
        when(recorder.latest(projectId)).thenReturn(Optional.of(source));
        return source;
    }

    @Test
    void rollbackRedeploysThePreviousSuccessfulVersionAsANewDeployment() {
        Deployment source = latestFailure();
        Deployment previous = version(2, "nginx:1.26-alpine", "abc1234", DeploymentStatus.RUNNING);
        when(recorder.previousSuccessful(projectId, 3, "ghcr.io/acme/app:bad")).thenReturn(Optional.of(previous));

        DeploymentResponse response = service.rollback(source.getId(), null);

        verify(recorder).createPending(projectId, "nginx:1.26-alpine", "abc1234", 2);
        assertThat(response.rollbackOfVersion()).isEqualTo(2);
        assertThat(response.status()).isEqualTo(DeploymentStatus.PENDING);
        assertThat(queued).hasSize(1);
        queued.get(0).run();
        verify(runner).run(deploymentId);
    }

    @Test
    void rollbackWarnsWhenTheRestoredTagCouldHaveMoved() {
        Deployment source = latestFailure();
        Deployment previous = version(2, "ghcr.io/acme/app:main", null, DeploymentStatus.RUNNING);
        when(recorder.previousSuccessful(projectId, 3, "ghcr.io/acme/app:bad")).thenReturn(Optional.of(previous));

        service.rollback(source.getId(), null);

        verify(recorder).log(eq(deploymentId), eq(LogLevel.WARN), contains("not a commit SHA"));
    }

    @Test
    void rollbackToACommitShaTagDoesNotWarn() {
        Deployment source = latestFailure();
        Deployment previous = version(2, "ghcr.io/acme/app:abc1234def", "abc1234def", DeploymentStatus.RUNNING);
        when(recorder.previousSuccessful(projectId, 3, "ghcr.io/acme/app:bad")).thenReturn(Optional.of(previous));

        service.rollback(source.getId(), null);

        verify(recorder, never()).log(any(), eq(LogLevel.WARN), anyString());
    }

    @Test
    void rollbackCanTargetASpecificVersion() {
        Deployment source = latestFailure();
        Deployment older = version(1, "nginx:1.25-alpine", null, DeploymentStatus.ROLLED_BACK);
        when(recorder.findVersion(projectId, 1)).thenReturn(Optional.of(older));

        service.rollback(source.getId(), new RollbackRequest(1));

        verify(recorder).createPending(projectId, "nginx:1.25-alpine", null, 1);
    }

    @Test
    void onlyTheLatestDeploymentCanBeRolledBack() {
        Deployment source = version(2, "img:2", null, DeploymentStatus.RUNNING);
        Deployment latest = version(3, "img:3", null, DeploymentStatus.RUNNING);
        when(recorder.get(source.getId())).thenReturn(source);
        when(recorder.latest(projectId)).thenReturn(Optional.of(latest));

        assertThatThrownBy(() -> service.rollback(source.getId(), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("#3");
        verify(recorder, never()).createPending(any(), anyString(), any(), any());
    }

    @Test
    void aDeploymentStillInProgressCannotBeRolledBack() {
        Deployment source = version(3, "img:3", null, DeploymentStatus.DEPLOYING);
        when(recorder.get(source.getId())).thenReturn(source);
        when(recorder.latest(projectId)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.rollback(source.getId(), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("in progress");
    }

    @Test
    void rollbackFailsWhenThereIsNothingToRollBackTo() {
        Deployment source = latestFailure();
        when(recorder.previousSuccessful(projectId, 3, "ghcr.io/acme/app:bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rollback(source.getId(), null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no earlier successful deployment");
        verify(recorder, never()).createPending(any(), anyString(), any(), any());
    }

    @Test
    void anUnknownTargetVersionIsNotFound() {
        Deployment source = latestFailure();
        when(recorder.findVersion(projectId, 9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rollback(source.getId(), new RollbackRequest(9)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theTargetMustBeOlderThanTheDeploymentBeingRolledBack() {
        Deployment source = latestFailure();
        when(recorder.findVersion(projectId, 3)).thenReturn(Optional.of(source));

        assertThatThrownBy(() -> service.rollback(source.getId(), new RollbackRequest(3)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("older");
    }

    @Test
    void theTargetMustHaveRunSuccessfully() {
        Deployment source = latestFailure();
        Deployment failed = version(2, "nginx:1.26-alpine", null, DeploymentStatus.FAILED);
        when(recorder.findVersion(projectId, 2)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> service.rollback(source.getId(), new RollbackRequest(2)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("never ran successfully");
    }

    @Test
    void theTargetMustUseADifferentImage() {
        Deployment source = latestFailure();
        Deployment sameImage = version(2, "ghcr.io/acme/app:bad", null, DeploymentStatus.RUNNING);
        when(recorder.findVersion(projectId, 2)).thenReturn(Optional.of(sameImage));

        assertThatThrownBy(() -> service.rollback(source.getId(), new RollbackRequest(2)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("same image");
    }

    @Test
    void aRollbackRejectedByTheRecorderQueuesNothing() {
        Deployment source = latestFailure();
        Deployment previous = version(2, "nginx:1.26-alpine", null, DeploymentStatus.RUNNING);
        when(recorder.previousSuccessful(projectId, 3, "ghcr.io/acme/app:bad")).thenReturn(Optional.of(previous));
        when(recorder.createPending(any(), anyString(), any(), any()))
                .thenThrow(new ConflictException("A deployment is already in progress for this project"));

        assertThatThrownBy(() -> service.rollback(source.getId(), null)).isInstanceOf(ConflictException.class);
        assertThat(queued).isEmpty();
    }

    @Test
    void aFullQueueFailsTheRollbackAndReportsBusy() {
        Deployment source = latestFailure();
        Deployment previous = version(2, "nginx:1.26-alpine", null, DeploymentStatus.RUNNING);
        when(recorder.previousSuccessful(projectId, 3, "ghcr.io/acme/app:bad")).thenReturn(Optional.of(previous));
        Executor rejecting = command -> {
            throw new RejectedExecutionException("queue full");
        };
        DeploymentService busy = new DeploymentService(
                projectRepository, imageResolver, recorder, runner, new DeploymentMapper(), rejecting);

        assertThatThrownBy(() -> busy.rollback(source.getId(), null)).isInstanceOf(ServiceBusyException.class);

        verify(recorder).markFailed(deploymentId, "Deployment queue is full");
    }

    @Test
    void getReturnsTheStoredDeployment() {
        Deployment deployment = new Deployment(projectId, 1, "ghcr.io/acme/app:main", null);
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
