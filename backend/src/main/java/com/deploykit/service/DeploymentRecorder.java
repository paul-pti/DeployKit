package com.deploykit.service;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentLog;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.LogLevel;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.repository.DeploymentLogRepository;
import com.deploykit.repository.DeploymentRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists deployment state and logs. Every public method is its own short transaction, so the long-running
 * workflow never holds a database connection while it waits on Helm or Kubernetes.
 */
@Service
@Transactional
public class DeploymentRecorder {

    static final int MAX_LOG_LENGTH = 8000;
    static final int MAX_ERROR_LENGTH = 2000;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogRepository logRepository;

    public DeploymentRecorder(DeploymentRepository deploymentRepository, DeploymentLogRepository logRepository) {
        this.deploymentRepository = deploymentRepository;
        this.logRepository = logRepository;
    }

    public Deployment createPending(UUID projectId, String image, String commitSha) {
        if (deploymentRepository.existsByProjectIdAndStatusIn(projectId, DeploymentStatus.ACTIVE)) {
            throw new ConflictException("A deployment is already in progress for this project");
        }
        // Flush now so the unique index (one active deployment per project) rejects a concurrent request here.
        Deployment deployment = deploymentRepository.saveAndFlush(new Deployment(projectId, image, commitSha));
        addLog(deployment.getId(), LogLevel.INFO, "Deployment queued for image " + image);
        return deployment;
    }

    @Transactional(readOnly = true)
    public Deployment get(UUID id) {
        return deploymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment " + id + " not found"));
    }

    public void markDeploying(UUID id) {
        get(id).markDeploying(Instant.now());
    }

    public void markRunning(UUID id) {
        get(id).markRunning(Instant.now());
        addLog(id, LogLevel.INFO, "Deployment is running");
    }

    public void markFailed(UUID id, String message) {
        get(id).markFailed(truncate(message, MAX_ERROR_LENGTH), Instant.now());
        addLog(id, LogLevel.ERROR, "Deployment failed: " + message);
    }

    public void log(UUID id, LogLevel level, String message) {
        addLog(id, level, message);
    }

    /** Fails deployments left in flight by a previous run of the backend; returns how many. */
    public int failInterrupted() {
        List<Deployment> stale = deploymentRepository.findAllByStatusIn(DeploymentStatus.ACTIVE);
        stale.forEach(deployment -> markFailed(deployment.getId(), "Interrupted by a backend restart"));
        return stale.size();
    }

    private void addLog(UUID deploymentId, LogLevel level, String message) {
        logRepository.save(new DeploymentLog(deploymentId, level, truncate(message, MAX_LOG_LENGTH), Instant.now()));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "… (truncated)";
    }
}
