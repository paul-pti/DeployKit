package com.deploykit.repository;

import com.deploykit.domain.DeploymentLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentLogRepository extends JpaRepository<DeploymentLog, UUID> {
}
