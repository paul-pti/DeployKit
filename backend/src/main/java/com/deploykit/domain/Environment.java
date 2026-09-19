package com.deploykit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;

/** A place a project is deployed to: a named environment mapped to a Kubernetes namespace. */
@Entity
@Table(name = "environments")
public class Environment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "kubernetes_namespace", nullable = false, length = 63)
    private String kubernetesNamespace;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Environment() {
        // required by JPA
    }

    public Environment(UUID projectId, String name, String kubernetesNamespace) {
        this.projectId = projectId;
        this.name = name;
        this.kubernetesNamespace = kubernetesNamespace;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getKubernetesNamespace() {
        return kubernetesNamespace;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
