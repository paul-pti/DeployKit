package com.deploykit.mapper;

import com.deploykit.domain.Deployment;
import com.deploykit.dto.DeploymentPage;
import com.deploykit.dto.DeploymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class DeploymentMapper {

    public DeploymentResponse toResponse(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(),
                deployment.getProjectId(),
                deployment.getVersion(),
                deployment.getStatus(),
                deployment.getImage(),
                deployment.getCommitSha(),
                deployment.getStartedAt(),
                deployment.getFinishedAt(),
                deployment.getCreatedAt(),
                deployment.getErrorMessage());
    }

    public DeploymentPage toPage(Page<Deployment> page) {
        return new DeploymentPage(
                page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
