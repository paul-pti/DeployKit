package com.deploykit.service;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.ImageReference;
import com.deploykit.domain.Project;
import com.deploykit.dto.DeployRequest;
import com.deploykit.dto.DeploymentResponse;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.exception.ServiceBusyException;
import com.deploykit.mapper.DeploymentMapper;
import com.deploykit.repository.ProjectRepository;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Entry point of the deployment engine. Validates the project, decides the image, records a PENDING deployment
 * and hands it to the deployment executor; the request returns without waiting for the rollout.
 */
@Service
public class DeploymentService {

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
        String commitSha = request != null && StringUtils.hasText(request.commitSha())
                ? request.commitSha().toLowerCase(Locale.ROOT)
                : null;

        Deployment deployment = recorder.createPending(project.getId(), image.toString(), commitSha);
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

    /** A restart kills in-flight deployments (single backend instance), so they must not stay "active" forever. */
    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedDeployments() {
        int failed = recorder.failInterrupted();
        if (failed > 0) {
            log.warn("Marked {} deployment(s) left in flight by a previous run as FAILED", failed);
        }
    }
}
