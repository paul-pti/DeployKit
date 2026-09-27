package com.deploykit.service;

import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.Project;
import com.deploykit.dto.CreateProjectRequest;
import com.deploykit.dto.ProjectResponse;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.DuplicateResourceException;
import com.deploykit.mapper.ProjectMapper;
import com.deploykit.repository.DeploymentRepository;
import com.deploykit.repository.EnvironmentRepository;
import com.deploykit.repository.ProjectRepository;
import com.deploykit.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    static final String DEFAULT_BRANCH = "main";

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final ProjectMapper projectMapper;
    private final DeploymentRepository deploymentRepository;
    private final EnvironmentRepository environmentRepository;
    private final KubernetesService kubernetesService;
    private final ProjectAccess access;

    public ProjectService(ProjectRepository projectRepository, ProjectMapper projectMapper,
                          DeploymentRepository deploymentRepository, EnvironmentRepository environmentRepository,
                          KubernetesService kubernetesService, ProjectAccess access) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
        this.deploymentRepository = deploymentRepository;
        this.environmentRepository = environmentRepository;
        this.kubernetesService = kubernetesService;
        this.access = access;
    }

    /** Creates a project owned by the caller; names only have to be unique among the caller's own projects. */
    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        CurrentUser user = access.currentUser();
        String name = request.name().trim();
        if (projectRepository.existsByOwnerIdAndName(user.id(), name)) {
            throw new DuplicateResourceException("A project named '" + name + "' already exists");
        }

        String branch = request.branch() == null || request.branch().isBlank()
                ? DEFAULT_BRANCH
                : request.branch().trim();

        Project saved = projectRepository.saveAndFlush(
                new Project(name, request.repositoryUrl().trim(), branch, request.port(), user.id()));
        log.info("Created project id={} name={}", saved.getId(), saved.getName());
        return projectMapper.toResponse(saved);
    }

    /** Administrators see every project, other users only their own. */
    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        CurrentUser user = access.currentUser();
        List<Project> projects = user.isAdmin()
                ? projectRepository.findAllByOrderByCreatedAtDesc()
                : projectRepository.findAllByOwnerIdOrderByCreatedAtDesc(user.id());
        return projects.stream().map(projectMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        return projectMapper.toResponse(access.requireProject(id));
    }

    @Transactional
    public void delete(UUID id) {
        Project project = access.requireProject(id);
        if (deploymentRepository.existsByProjectIdAndStatusIn(id, DeploymentStatus.ACTIVE)) {
            throw new ConflictException("Cannot delete a project while a deployment is in progress");
        }
        environmentRepository.findAllByProjectId(id)
                .forEach(environment -> removeNamespace(environment.getKubernetesNamespace()));
        projectRepository.delete(project);
        log.info("Deleted project id={}", id);
    }

    /** Best effort: an unreachable cluster must not make a project undeletable, but the leak is logged. */
    private void removeNamespace(String namespace) {
        try {
            kubernetesService.deleteNamespace(namespace);
        } catch (RuntimeException e) {
            log.warn("Could not delete Kubernetes namespace {}; remove it manually", namespace, e);
        }
    }
}
