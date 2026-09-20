package com.deploykit.repository;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    boolean existsByProjectIdAndStatusIn(UUID projectId, Collection<DeploymentStatus> statuses);

    List<Deployment> findAllByStatusIn(Collection<DeploymentStatus> statuses);

    Page<Deployment> findByProjectId(UUID projectId, Pageable pageable);

    Page<Deployment> findByProjectIdAndStatusIn(UUID projectId, Collection<DeploymentStatus> statuses, Pageable pageable);

    /** Highest version number used by the project, or 0 when it has no deployment yet. */
    @Query("select coalesce(max(d.version), 0) from Deployment d where d.projectId = :projectId")
    int currentVersion(@Param("projectId") UUID projectId);
}
