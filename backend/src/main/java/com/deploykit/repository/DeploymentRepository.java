package com.deploykit.repository;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    boolean existsByProjectIdAndStatusIn(UUID projectId, Collection<DeploymentStatus> statuses);

    List<Deployment> findAllByStatusIn(Collection<DeploymentStatus> statuses);
}
