package com.deploykit.mapper;

import com.deploykit.domain.Deployment;
import com.deploykit.dto.DeploymentResponse;
import org.springframework.stereotype.Component;

@Component
public class DeploymentMapper {

    public DeploymentResponse toResponse(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getProjectId(),
                deployment.getStatus(),
                deployment.getImage(),
                deployment.getCommitSha(),
                deployment.getStartedAt(),
                deployment.getFinishedAt(),
                deployment.getCreatedAt(),
                deployment.getErrorMessage());
    }
}
