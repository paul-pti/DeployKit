package com.deploykit.repository;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    Optional<Deployment> findFirstByProjectIdOrderByVersionDesc(UUID projectId);

    Optional<Deployment> findByProjectIdAndVersion(UUID projectId, int version);

    /** The most recent earlier deployment that succeeded with an image other than {@code image}. */
    Optional<Deployment> findFirstByProjectIdAndVersionLessThanAndStatusInAndImageNotOrderByVersionDesc(
            UUID projectId, int version, Collection<DeploymentStatus> statuses, String image);

    /** Deployments with the given status whose version lies strictly between {@code after} and {@code before}. */
    @Query("select d from Deployment d where d.projectId = :projectId and d.status = :status"
            + " and d.version > :after and d.version < :before")
    List<Deployment> findWithStatusBetweenVersions(@Param("projectId") UUID projectId,
                                                   @Param("status") DeploymentStatus status,
                                                   @Param("after") int after,
                                                   @Param("before") int before);
}
