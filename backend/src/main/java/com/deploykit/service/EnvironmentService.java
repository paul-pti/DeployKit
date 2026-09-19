package com.deploykit.service;

import com.deploykit.domain.Environment;
import com.deploykit.domain.Project;
import com.deploykit.repository.EnvironmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnvironmentService {

    private final EnvironmentRepository environmentRepository;

    public EnvironmentService(EnvironmentRepository environmentRepository) {
        this.environmentRepository = environmentRepository;
    }

    /** Returns the project's default environment, creating it (and choosing its namespace) on first use. */
    @Transactional
    public Environment getOrCreateDefault(Project project) {
        return environmentRepository.findByProjectIdAndName(project.getId(), ProjectNaming.DEFAULT_ENVIRONMENT)
                .orElseGet(() -> environmentRepository.save(new Environment(
                        project.getId(), ProjectNaming.DEFAULT_ENVIRONMENT, ProjectNaming.namespace(project))));
    }
}
