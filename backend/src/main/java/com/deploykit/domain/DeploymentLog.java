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

@Entity
@Table(name = "deployment_logs")
public class DeploymentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deployment_id", nullable = false, updatable = false)
    private UUID deploymentId;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant loggedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LogLevel level;

    @Column(nullable = false, columnDefinition = "text")
    private String message;

    protected DeploymentLog() {
        // required by JPA
    }

    public DeploymentLog(UUID deploymentId, LogLevel level, String message, Instant loggedAt) {
        this.deploymentId = deploymentId;
        this.level = level;
        this.message = message;
        this.loggedAt = loggedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDeploymentId() {
        return deploymentId;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }
}
