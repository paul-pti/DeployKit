package com.deploykit.repository;

import com.deploykit.domain.DeploymentLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, UUID> {

    List<DeploymentLog> findByDeploymentId(UUID deploymentId, Pageable pageable);
}
