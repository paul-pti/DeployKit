package com.deploykit.service;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.Project;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.repository.ProjectRepository;
import com.deploykit.security.CurrentUser;
import com.deploykit.security.CurrentUserProvider;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single place that decides whether the caller may act on a project or one of its deployments: administrators on
 * everything, users on their own projects. A resource the caller may not access is reported as <em>not found</em>,
 * exactly like one that does not exist, so its existence cannot be probed.
 */
@Component
public class ProjectAccess {

    private final ProjectRepository projectRepository;
    private final DeploymentRecorder recorder;
    private final CurrentUserProvider currentUserProvider;

    public ProjectAccess(ProjectRepository projectRepository, DeploymentRecorder recorder,
                         CurrentUserProvider currentUserProvider) {
        this.projectRepository = projectRepository;
        this.recorder = recorder;
        this.currentUserProvider = currentUserProvider;
    }

    public CurrentUser currentUser() {
        return currentUserProvider.require();
    }

    public Project requireProject(UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> projectNotFound(projectId));
        if (!currentUser().canAccess(project.getOwnerId())) {
            throw projectNotFound(projectId);
        }
        return project;
    }

    public Deployment requireDeployment(UUID deploymentId) {
        Deployment deployment = recorder.get(deploymentId);
        Project project = projectRepository.findById(deployment.getProjectId())
                .orElseThrow(() -> deploymentNotFound(deploymentId));
        if (!currentUser().canAccess(project.getOwnerId())) {
            throw deploymentNotFound(deploymentId);
        }
        return deployment;
    }

    private static ResourceNotFoundException projectNotFound(UUID projectId) {
        return new ResourceNotFoundException("Project " + projectId + " not found");
    }

    private static ResourceNotFoundException deploymentNotFound(UUID deploymentId) {
        return new ResourceNotFoundException("Deployment " + deploymentId + " not found");
    }
}
