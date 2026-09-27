package com.deploykit.service;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.ImageReference;
import com.deploykit.domain.LogLevel;
import com.deploykit.domain.Project;
import com.deploykit.dto.DeployRequest;
import com.deploykit.dto.DeploymentPage;
import com.deploykit.dto.DeploymentResponse;
import com.deploykit.dto.RollbackRequest;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.exception.ServiceBusyException;
import com.deploykit.mapper.DeploymentMapper;
import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Entry point of the deployment engine. Validates the project, decides the image, records a PENDING deployment
 * and hands it to the deployment executor; the request returns without waiting for the rollout.
 */
@Service
public class DeploymentService {

    static final int MAX_PAGE_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);

    private final ProjectAccess access;
    private final ImageResolver imageResolver;
    private final DeploymentRecorder recorder;
    private final DeploymentRunner runner;
    private final DeploymentMapper mapper;
    private final Executor deploymentExecutor;

    public DeploymentService(ProjectAccess access, ImageResolver imageResolver,
                             DeploymentRecorder recorder, DeploymentRunner runner, DeploymentMapper mapper,
                             @Qualifier("deploymentExecutor") Executor deploymentExecutor) {
        this.access = access;
        this.imageResolver = imageResolver;
        this.recorder = recorder;
        this.runner = runner;
        this.mapper = mapper;
        this.deploymentExecutor = deploymentExecutor;
    }

    public DeploymentResponse deploy(UUID projectId, DeployRequest request) {
        Project project = access.requireProject(projectId);
        ImageReference image = imageResolver.resolve(project, request);
        return queue(project.getId(), image.toString(), commitShaOf(request, image), null);
    }

    /**
     * Rolls a deployment back by redeploying an earlier version. Only the project's latest deployment can be rolled
     * back. The target is the requested version, or by default the most recent earlier version that ran
     * successfully with a different image. The rollback is a new deployment (with its own version number) that goes
     * through the normal workflow.
     */
    public DeploymentResponse rollback(UUID deploymentId, RollbackRequest request) {
        Deployment source = access.requireDeployment(deploymentId);
        UUID projectId = source.getProjectId();

        Deployment latest = recorder.latest(projectId).orElse(source);
        if (latest.getVersion() != source.getVersion()) {
            throw new ConflictException(
                    "Only the latest deployment (#%d) can be rolled back".formatted(latest.getVersion()));
        }
        if (source.getStatus().isActive()) {
            throw new ConflictException("The deployment is still in progress, wait for it to finish");
        }

        Deployment target = request != null && request.targetVersion() != null
                ? explicitTarget(source, request.targetVersion())
                : recorder.previousSuccessful(projectId, source.getVersion(), source.getImage())
                        .orElseThrow(() -> new ConflictException(
                                "There is no earlier successful deployment with a different image to roll back to"));
        return queue(projectId, target.getImage(), target.getCommitSha(), target.getVersion());
    }

    private Deployment explicitTarget(Deployment source, int version) {
        Deployment target = recorder.findVersion(source.getProjectId(), version)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment version %d not found".formatted(version)));
        if (target.getVersion() >= source.getVersion()) {
            throw new ConflictException("The target version must be older than the deployment being rolled back");
        }
        if (!DeploymentStatus.SUCCEEDED.contains(target.getStatus())) {
            throw new ConflictException("Version %d never ran successfully (%s)".formatted(version, target.getStatus()));
        }
        if (target.getImage().equals(source.getImage())) {
            throw new ConflictException(
                    "Version %d uses the same image as the deployment being rolled back".formatted(version));
        }
        return target;
    }

    /** Records a PENDING deployment (a rollback when {@code rollbackOfVersion} is set) and hands it to the executor. */
    private DeploymentResponse queue(UUID projectId, String image, String commitSha, Integer rollbackOfVersion) {
        Deployment deployment = rollbackOfVersion == null
                ? recorder.createPending(projectId, image, commitSha)
                : recorder.createPending(projectId, image, commitSha, rollbackOfVersion);
        if (rollbackOfVersion != null) {
            warnIfTagIsMutable(deployment.getId(), image);
        }
        try {
            deploymentExecutor.execute(() -> runner.run(deployment.getId()));
        } catch (RejectedExecutionException e) {
            recorder.markFailed(deployment.getId(), "Deployment queue is full");
            throw new ServiceBusyException("Too many deployments in progress, try again later");
        }
        log.info("Queued deployment {} of project {} with image {}", deployment.getId(), projectId, image);
        return mapper.toResponse(deployment);
    }

    /** A rollback is only exact for immutable tags: a moving tag (a branch name) may now point to newer content. */
    private void warnIfTagIsMutable(UUID deploymentId, String image) {
        ImageReference reference = ImageReference.parse(image);
        if (!reference.isCommitSha()) {
            recorder.log(deploymentId, LogLevel.WARN,
                    "Image tag '%s' is not a commit SHA: the registry may now serve different content than when it was "
                            .formatted(reference.tag())
                            + "first deployed. Deploy commit-SHA tags to make rollbacks exact.");
        }
    }

    public DeploymentResponse get(UUID id) {
        return mapper.toResponse(access.requireDeployment(id));
    }

    /** A project's deployment history, newest first, optionally restricted to some statuses. */
    public DeploymentPage list(UUID projectId, Collection<DeploymentStatus> statuses, int page, int size) {
        access.requireProject(projectId);
        // The sort is fixed here: clients cannot choose it, only the page.
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.clamp(size, 1, MAX_PAGE_SIZE),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("version")));
        return mapper.toPage(recorder.list(projectId, statuses, pageable));
    }

    /** The commit is known when the request names it, or when the image itself is tagged with a commit SHA. */
    private static String commitShaOf(DeployRequest request, ImageReference image) {
        if (request != null && StringUtils.hasText(request.commitSha())) {
            return request.commitSha().toLowerCase(Locale.ROOT);
        }
        return image.isCommitSha() ? image.tag() : null;
    }

    /** A restart kills in-flight deployments (single backend instance), so they must not stay "active" forever. */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedDeployments() {
        int failed = recorder.failInterrupted();
        if (failed > 0) {
            log.warn("Marked {} deployment(s) left in flight by a previous run as FAILED", failed);
        }
    }
}
