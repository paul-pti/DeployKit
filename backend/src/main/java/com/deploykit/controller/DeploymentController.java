package com.deploykit.controller;

import com.deploykit.domain.DeploymentStatus;
import com.deploykit.dto.DeployRequest;
import com.deploykit.dto.DeploymentPage;
import com.deploykit.dto.DeploymentResponse;
import com.deploykit.service.DeploymentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Deployment history, newest first. {@code status} may be repeated to keep several statuses;
     * {@code size} is capped at 100.
     */
    @GetMapping("/api/projects/{projectId}/deployments")
    public DeploymentPage list(
            @PathVariable UUID projectId,
            @RequestParam(name = "status", required = false) List<DeploymentStatus> statuses,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return deploymentService.list(projectId, statuses, page, size);
    }

    @GetMapping("/api/deployments/{id}")
    public DeploymentResponse get(@PathVariable UUID id) {
        return deploymentService.get(id);
    }
}
