package com.deploykit.service;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.ImageReference;
import com.deploykit.domain.Project;
import com.deploykit.dto.DeployRequest;
import com.deploykit.dto.DeploymentPage;
import com.deploykit.dto.DeploymentResponse;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.exception.ServiceBusyException;
import com.deploykit.mapper.DeploymentMapper;
import com.deploykit.repository.ProjectRepository;
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

    private final ProjectRepository projectRepository;
    private final ImageResolver imageResolver;
    private final DeploymentRecorder recorder;
    private final DeploymentRunner runner;
    private final DeploymentMapper mapper;
    private final Executor deploymentExecutor;

    public DeploymentService(ProjectRepository projectRepository, ImageResolver imageResolver,
                             DeploymentRecorder recorder, DeploymentRunner runner, DeploymentMapper mapper,
                             @Qualifier("deploymentExecutor") Executor deploymentExecutor) {
        this.projectRepository = projectRepository;
        this.imageResolver = imageResolver;
        this.recorder = recorder;
        this.runner = runner;
        this.mapper = mapper;
        this.deploymentExecutor = deploymentExecutor;
    }

    public DeploymentResponse deploy(UUID projectId, DeployRequest request) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + projectId + " not found"));
        ImageReference image = imageResolver.resolve(project, request);
        Deployment deployment = recorder.createPending(project.getId(), image.toString(), commitShaOf(request, image));
        try {
            deploymentExecutor.execute(() -> runner.run(deployment.getId()));
        } catch (RejectedExecutionException e) {
            recorder.markFailed(deployment.getId(), "Deployment queue is full");
            throw new ServiceBusyException("Too many deployments in progress, try again later");
        }
        log.info("Queued deployment {} of project {} with image {}", deployment.getId(), projectId, image);
        return mapper.toResponse(deployment);
    }

    public DeploymentResponse get(UUID id) {
        return mapper.toResponse(recorder.get(id));
    }

    /** A project's deployment history, newest first, optionally restricted to some statuses. */
    public DeploymentPage list(UUID projectId, Collection<DeploymentStatus> statuses, int page, int size) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project " + projectId + " not found");
        }
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
