package com.deploykit.service;

import com.deploykit.domain.Project;
import com.deploykit.dto.CreateProjectRequest;
import com.deploykit.dto.ProjectResponse;
import com.deploykit.exception.DuplicateResourceException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.mapper.ProjectMapper;
import com.deploykit.repository.ProjectRepository;
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

    public ProjectService(ProjectRepository projectRepository, ProjectMapper projectMapper) {
        this.projectRepository = projectRepository;
        this.projectMapper = projectMapper;
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        String name = request.name().trim();
        if (projectRepository.existsByName(name)) {
            throw new DuplicateResourceException("A project named '" + name + "' already exists");
        }

        String branch = request.branch() == null || request.branch().isBlank()
                ? DEFAULT_BRANCH
                : request.branch().trim();

        Project saved = projectRepository.saveAndFlush(
                new Project(name, request.repositoryUrl().trim(), branch, request.port()));
        log.info("Created project id={} name={}", saved.getId(), saved.getName());
        return projectMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list() {
        return projectRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(projectMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(UUID id) {
        return projectMapper.toResponse(findOrThrow(id));
    }

    @Transactional
    public void delete(UUID id) {
        projectRepository.delete(findOrThrow(id));
        log.info("Deleted project id={}", id);
    }

    private Project findOrThrow(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + id + " not found"));
    }
}
