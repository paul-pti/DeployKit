package com.deploykit.controller;

import com.deploykit.domain.DeploymentStatus;
import com.deploykit.dto.DeployRequest;
import com.deploykit.dto.DeploymentLogsResponse;
import com.deploykit.dto.DeploymentPage;
import com.deploykit.dto.DeploymentResponse;
import com.deploykit.dto.RollbackRequest;
import com.deploykit.service.DeploymentLogService;
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
    private final DeploymentLogService deploymentLogService;

    public DeploymentController(DeploymentService deploymentService, DeploymentLogService deploymentLogService) {
        this.deploymentService = deploymentService;
        this.deploymentLogService = deploymentLogService;
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

    /**
     * Logs of a deployment: the last {@code tail} lines of each pod running its image (or of their previous
     * container with {@code previous=true}) and the events of the deployment workflow.
     */
    @GetMapping("/api/deployments/{id}/logs")
    public DeploymentLogsResponse logs(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "200") int tail,
            @RequestParam(defaultValue = "false") boolean previous) {
        return deploymentLogService.getLogs(id, tail, previous);
    }

    /**
     * Rolls the latest deployment back by redeploying an earlier successful version, as a new deployment. The optional
     * body {@code {"targetVersion": 2}} picks the version to restore. Returns immediately like a deploy.
     */
    @PostMapping("/api/deployments/{id}/rollback")
    public ResponseEntity<DeploymentResponse> rollback(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) RollbackRequest request) {
        DeploymentResponse rollback = deploymentService.rollback(id, request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/deployments/" + rollback.id()))
                .body(rollback);
    }

    @GetMapping("/api/deployments/{id}")
    public DeploymentResponse get(@PathVariable UUID id) {
        return deploymentService.get(id);
    }
}
