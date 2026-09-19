package com.deploykit.repository;

import com.deploykit.domain.Project;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByName(String name);

    List<Project> findAllByOrderByCreatedAtDesc();
}
