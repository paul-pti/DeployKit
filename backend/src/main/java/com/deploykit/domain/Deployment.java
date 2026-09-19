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

    public Deployment(UUID projectId, String image, String commitSha) {
        this.projectId = projectId;
        this.image = image;
        this.commitSha = commitSha;
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
