package com.deploykit.dto;

import com.deploykit.domain.Role;
import java.time.Instant;
import java.util.UUID;

/** A user account. The password hash is never part of any response. */
public record UserResponse(UUID id, String email, Role role, Instant createdAt) {
}
