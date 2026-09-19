package com.deploykit.controller;

import com.deploykit.dto.DeployRequest;
import com.deploykit.dto.DeploymentResponse;
import com.deploykit.service.DeploymentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DeploymentController {

    private final DeploymentService deploymentService;

    public DeploymentController(DeploymentService deploymentService) {
        this.deploymentService = deploymentService;
    }

    /** Starts a deployment and returns immediately; poll the Location URL for progress. */
    @PostMapping("/api/projects/{projectId}/deploy")
    public ResponseEntity<DeploymentResponse> deploy(
            @PathVariable UUID projectId,
            @Valid @RequestBody(required = false) DeployRequest request) {
        DeploymentResponse deployment = deploymentService.deploy(projectId, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/deployments/" + deployment.id()))
                .body(deployment);
    }

    @GetMapping("/api/deployments/{id}")
    public DeploymentResponse get(@PathVariable UUID id) {
        return deploymentService.get(id);
    }
}
