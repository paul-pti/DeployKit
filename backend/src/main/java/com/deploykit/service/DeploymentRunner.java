package com.deploykit.service;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.Environment;
import com.deploykit.domain.ImageReference;
import com.deploykit.domain.LogLevel;
import com.deploykit.domain.Project;
import com.deploykit.dto.HelmRelease;
import com.deploykit.exception.HelmOperationException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.repository.ProjectRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Runs the deployment workflow for one deployment: helm upgrade, rollout monitoring, final status. Meant to be
 * executed on the deployment executor; it never throws, every outcome ends up in the deployment record.
 */
@Component
public class DeploymentRunner {

    private static final Logger log = LoggerFactory.getLogger(DeploymentRunner.class);

    private final DeploymentRecorder recorder;
    private final ProjectRepository projectRepository;
    private final EnvironmentService environmentService;
    private final KubernetesService kubernetesService;
    private final HelmService helmService;
    private final RolloutMonitor rolloutMonitor;

    public DeploymentRunner(DeploymentRecorder recorder, ProjectRepository projectRepository,
                            EnvironmentService environmentService, KubernetesService kubernetesService,
                            HelmService helmService, RolloutMonitor rolloutMonitor) {
        this.recorder = recorder;
        this.projectRepository = projectRepository;
        this.environmentService = environmentService;
        this.kubernetesService = kubernetesService;
        this.helmService = helmService;
        this.rolloutMonitor = rolloutMonitor;
    }

    public void run(UUID deploymentId) {
        Deployment deployment;
        try {
            deployment = recorder.get(deploymentId);
        } catch (ResourceNotFoundException e) {
            log.warn("Deployment {} no longer exists, nothing to run", deploymentId);
            return;
        }

        try (MDC.MDCCloseable ignoredDeployment = MDC.putCloseable("deploymentId", deploymentId.toString());
             MDC.MDCCloseable ignoredProject = MDC.putCloseable("projectId", deployment.getProjectId().toString())) {
            execute(deployment);
        }
    }

    private void execute(Deployment deployment) {
        UUID id = deployment.getId();
        try {
            recorder.markDeploying(id);

            Project project = projectRepository.findById(deployment.getProjectId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project no longer exists"));
            String app = ProjectNaming.appName(project);
            Environment environment = environmentService.getOrCreateDefault(project);
            String namespace = environment.getKubernetesNamespace();
            ImageReference image = ImageReference.parse(deployment.getImage());

            recorder.log(id, LogLevel.INFO,
                    "Deploying %s to namespace %s as release %s".formatted(image, namespace, app));
            kubernetesService.ensureNamespace(namespace);

            String helmOutput = helmService.upgradeInstall(new HelmRelease(app, namespace, image, project.getPort()));
            recorder.log(id, LogLevel.INFO, "Helm release applied:\n" + helmOutput.strip());

            RolloutResult rollout = rolloutMonitor.await(namespace, app, message -> recorder.log(id, LogLevel.INFO, message));
            if (rollout.success()) {
                recorder.markRunning(id);
            } else {
                recorder.markFailed(id, rollout.message());
            }
        } catch (HelmOperationException e) {
            log.error("Helm failed for deployment {}: {}", id, e.getMessage());
            if (!e.getOutput().isBlank()) {
                recordQuietly(id, LogLevel.ERROR, "Helm output:\n" + e.getOutput().strip());
            }
            failQuietly(id, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failQuietly(id, "Deployment interrupted");
        } catch (RuntimeException e) {
            log.error("Deployment {} failed unexpectedly", id, e);
            failQuietly(id, "Unexpected error: " + e.getMessage());
        }
    }

    private void failQuietly(UUID id, String message) {
        try {
            recorder.markFailed(id, message);
        } catch (RuntimeException e) {
            log.error("Could not record failure of deployment {}", id, e);
        }
    }

    private void recordQuietly(UUID id, LogLevel level, String message) {
        try {
            recorder.log(id, level, message);
        } catch (RuntimeException e) {
            log.error("Could not record log of deployment {}", id, e);
        }
    }
}
