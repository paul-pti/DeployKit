package com.deploykit.repository;

import com.deploykit.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Emails are stored lowercase, so callers pass a normalised email. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
