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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    static final int MAX_EVENTS = 1000;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLogRepository logRepository;

    public DeploymentRecorder(DeploymentRepository deploymentRepository, DeploymentLogRepository logRepository) {
        this.deploymentRepository = deploymentRepository;
        this.logRepository = logRepository;
    }

    public Deployment createPending(UUID projectId, String image, String commitSha) {
        return createPending(projectId, image, commitSha, null);
    }

    /** Same, for a rollback: {@code rollbackOfVersion} is the version whose image is being restored. */
    public Deployment createPending(UUID projectId, String image, String commitSha, Integer rollbackOfVersion) {
        if (deploymentRepository.existsByProjectIdAndStatusIn(projectId, DeploymentStatus.ACTIVE)) {
            throw new ConflictException("A deployment is already in progress for this project");
        }
        // Flush now so the unique index (one active deployment per project) rejects a concurrent request here.
        int version = deploymentRepository.currentVersion(projectId) + 1;
        Deployment deployment = deploymentRepository.saveAndFlush(
                new Deployment(projectId, version, image, commitSha, rollbackOfVersion));
        addLog(deployment.getId(), LogLevel.INFO, rollbackOfVersion == null
                ? "Deployment queued for image " + image
                : "Rollback to version %d queued (image %s)".formatted(rollbackOfVersion, image));
        return deployment;
    }

    @Transactional(readOnly = true)
    public Deployment get(UUID id) {
        return deploymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment " + id + " not found"));
    }

    /** The most recent deployment of the project, if any. */
    @Transactional(readOnly = true)
    public Optional<Deployment> latest(UUID projectId) {
        return deploymentRepository.findFirstByProjectIdOrderByVersionDesc(projectId);
    }

    @Transactional(readOnly = true)
    public Optional<Deployment> findVersion(UUID projectId, int version) {
        return deploymentRepository.findByProjectIdAndVersion(projectId, version);
    }

    /** The most recent version before {@code beforeVersion} that ran successfully with an image other than the given one. */
    @Transactional(readOnly = true)
    public Optional<Deployment> previousSuccessful(UUID projectId, int beforeVersion, String excludedImage) {
        return deploymentRepository.findFirstByProjectIdAndVersionLessThanAndStatusInAndImageNotOrderByVersionDesc(
                projectId, beforeVersion, DeploymentStatus.SUCCEEDED, excludedImage);
    }

    /** The workflow log of a deployment, oldest first (at most 1000 entries). */
    @Transactional(readOnly = true)
    public List<DeploymentLog> events(UUID deploymentId) {
        return logRepository.findByDeploymentId(
                deploymentId, PageRequest.of(0, MAX_EVENTS, Sort.by(Sort.Order.asc("loggedAt"))));
    }

    @Transactional(readOnly = true)
    public Page<Deployment> list(UUID projectId, Collection<DeploymentStatus> statuses, Pageable pageable) {
        return statuses == null || statuses.isEmpty()
                ? deploymentRepository.findByProjectId(projectId, pageable)
                : deploymentRepository.findByProjectIdAndStatusIn(projectId, statuses, pageable);
    }

    public void markDeploying(UUID id) {
        get(id).markDeploying(Instant.now());
    }

    /**
     * Marks the deployment RUNNING. When it is a rollback, the deployments it replaces (those that were RUNNING
     * between the restored version and this one) become ROLLED_BACK in the same transaction, so history never
     * shows a rollback as done while the deployments it undid still look current.
     */
    public void markRunning(UUID id) {
        Deployment deployment = get(id);
        deployment.markRunning(Instant.now());
        addLog(id, LogLevel.INFO, "Deployment is running");
        if (deployment.getRollbackOfVersion() != null) {
            markReplacedAsRolledBack(deployment);
        }
    }

    private void markReplacedAsRolledBack(Deployment rollback) {
        deploymentRepository.findWithStatusBetweenVersions(
                        rollback.getProjectId(), DeploymentStatus.RUNNING,
                        rollback.getRollbackOfVersion(), rollback.getVersion())
                .forEach(replaced -> {
                    replaced.markRolledBack();
                    addLog(replaced.getId(), LogLevel.INFO, "Rolled back by deployment #%d (restored version %d)"
                            .formatted(rollback.getVersion(), rollback.getRollbackOfVersion()));
                });
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
