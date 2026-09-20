package com.deploykit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "deployments")
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    /** Per-project deployment number (1, 2, 3, ...), unique within a project. */
    @Column(nullable = false, updatable = false)
    private int version;

    /** For a rollback: the version whose image is restored; null for a regular deployment. */
    @Column(name = "rollback_of_version", updatable = false)
    private Integer rollbackOfVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeploymentStatus status = DeploymentStatus.PENDING;

    @Column(length = 500)
    private String image;

    @Column(name = "commit_sha", length = 64)
    private String commitSha;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    protected Deployment() {
        // required by JPA
    }

    public Deployment(UUID projectId, int version, String image, String commitSha) {
        this(projectId, version, image, commitSha, null);
    }

    public Deployment(UUID projectId, int version, String image, String commitSha, Integer rollbackOfVersion) {
        this.projectId = projectId;
        this.version = version;
        this.image = image;
        this.commitSha = commitSha;
        this.rollbackOfVersion = rollbackOfVersion;
    }

    public void markDeploying(Instant now) {
        requireActive("start");
        this.status = DeploymentStatus.DEPLOYING;
        this.startedAt = now;
    }

    public void markRunning(Instant now) {
        if (status != DeploymentStatus.DEPLOYING) {
            throw new IllegalStateException("Cannot mark a " + status + " deployment as RUNNING");
        }
        this.status = DeploymentStatus.RUNNING;
        this.finishedAt = now;
    }

    public void markFailed(String message, Instant now) {
        requireActive("fail");
        this.status = DeploymentStatus.FAILED;
        this.errorMessage = message;
        this.finishedAt = now;
    }

    /** A deployment that ran successfully and was later replaced by a rollback. */
    public void markRolledBack() {
        if (status != DeploymentStatus.RUNNING) {
            throw new IllegalStateException("Cannot roll back a " + status + " deployment");
        }
        this.status = DeploymentStatus.ROLLED_BACK;
    }

    private void requireActive(String action) {
        if (!status.isActive()) {
            throw new IllegalStateException("Cannot " + action + " a " + status + " deployment");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public int getVersion() {
        return version;
    }

    public Integer getRollbackOfVersion() {
        return rollbackOfVersion;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public String getImage() {
        return image;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
