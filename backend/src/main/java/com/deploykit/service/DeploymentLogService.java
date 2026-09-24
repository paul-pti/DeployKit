package com.deploykit.service;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.Environment;
import com.deploykit.domain.Project;
import com.deploykit.dto.DeploymentEvent;
import com.deploykit.dto.DeploymentLogsResponse;
import com.deploykit.dto.PodInfo;
import com.deploykit.dto.PodLogs;
import com.deploykit.exception.KubernetesOperationException;
import com.deploykit.exception.PodLogsUnavailableException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.repository.EnvironmentRepository;
import com.deploykit.repository.ProjectRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Reads what a deployment produced: the logs of the pods running its image, and the events of the deployment
 * workflow. One failing part never hides the others: a pod whose logs cannot be read is reported on its own, and an
 * unreachable cluster still leaves the workflow events available.
 */
@Service
public class DeploymentLogService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentLogService.class);

    private final DeploymentRecorder recorder;
    private final ProjectRepository projectRepository;
    private final EnvironmentRepository environmentRepository;
    private final KubernetesService kubernetesService;
    private final ProjectAccess access;

    public DeploymentLogService(DeploymentRecorder recorder, ProjectRepository projectRepository,
                                EnvironmentRepository environmentRepository, KubernetesService kubernetesService,
                                ProjectAccess access) {
        this.recorder = recorder;
        this.projectRepository = projectRepository;
        this.environmentRepository = environmentRepository;
        this.kubernetesService = kubernetesService;
        this.access = access;
    }

    private record PodsResult(List<PodLogs> pods, String note) {

        static PodsResult noPods(String note) {
            return new PodsResult(List.of(), note);
        }
    }

    public DeploymentLogsResponse getLogs(UUID deploymentId, int tailLines, boolean previous) {
        Deployment deployment = access.requireDeployment(deploymentId);
        List<DeploymentEvent> events = recorder.events(deploymentId).stream()
                .map(entry -> new DeploymentEvent(entry.getLoggedAt(), entry.getLevel(), entry.getMessage()))
                .toList();
        PodsResult pods = readPods(deployment, tailLines, previous);
        return new DeploymentLogsResponse(deployment.getId(), deployment.getStatus(), pods.pods(), pods.note(), events);
    }

    private PodsResult readPods(Deployment deployment, int tailLines, boolean previous) {
        if (deployment.getStatus() == DeploymentStatus.PENDING || deployment.getStatus() == DeploymentStatus.BUILDING) {
            return PodsResult.noPods("The deployment has not started yet, there are no pods.");
        }

        Optional<Environment> environment =
                environmentRepository.findByProjectIdAndName(deployment.getProjectId(), ProjectNaming.DEFAULT_ENVIRONMENT);
        Optional<Project> project = projectRepository.findById(deployment.getProjectId());
        if (environment.isEmpty() || project.isEmpty()) {
            return PodsResult.noPods("Nothing has been deployed for this project yet.");
        }
        String namespace = environment.get().getKubernetesNamespace();
        String app = ProjectNaming.appName(project.get());

        List<PodInfo> pods;
        try {
            pods = kubernetesService.getPods(namespace, app);
        } catch (KubernetesOperationException e) {
            log.warn("Cannot list the pods of {}/{}: {}", namespace, app, e.getMessage());
            return PodsResult.noPods("Kubernetes is unreachable, pod logs are unavailable right now.");
        }

        // Pods of older deployments run other images; a pod belongs to this deployment when it runs its image.
        List<PodInfo> mine = pods.stream().filter(pod -> deployment.getImage().equals(pod.image())).toList();
        if (mine.isEmpty()) {
            return PodsResult.noPods(pods.isEmpty()
                    ? "No pods exist for this application."
                    : "No pod runs this deployment's image anymore: a newer deployment replaced them.");
        }
        return new PodsResult(mine.stream().map(pod -> readPod(namespace, pod, tailLines, previous)).toList(), null);
    }

    private PodLogs readPod(String namespace, PodInfo pod, int tailLines, boolean previous) {
        String text = "";
        String error = null;
        try {
            text = kubernetesService.getPodLogs(namespace, pod.name(), tailLines, previous);
        } catch (PodLogsUnavailableException e) {
            error = e.getMessage();
        } catch (ResourceNotFoundException e) {
            error = "The pod no longer exists.";
        } catch (KubernetesOperationException e) {
            error = "The logs of this pod could not be read.";
        }
        return new PodLogs(pod.name(), pod.phase(), pod.ready(), pod.restarts(), pod.reason(), text, error);
    }
}
