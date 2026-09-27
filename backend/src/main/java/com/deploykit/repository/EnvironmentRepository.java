package com.deploykit.repository;

import com.deploykit.domain.Environment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    Optional<Environment> findByProjectIdAndName(UUID projectId, String name);

    List<Environment> findAllByProjectId(UUID projectId);
}
