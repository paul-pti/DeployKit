package com.deploykit.mapper;

import com.deploykit.domain.Project;
import com.deploykit.dto.ProjectResponse;
import org.springframework.stereotype.Component;

@Component
public class ProjectMapper {

    public ProjectResponse toResponse(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getName(),
                project.getRepositoryUrl(),
                project.getBranch(),
                project.getPort(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
