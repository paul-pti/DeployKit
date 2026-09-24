package com.deploykit.security;

import com.deploykit.domain.Role;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, as read from a verified JWT. */
public record CurrentUser(UUID id, Role role) {

    public static final String ROLE_CLAIM = "role";
    public static final String EMAIL_CLAIM = "email";

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(UUID.fromString(jwt.getSubject()), Role.valueOf(jwt.getClaimAsString(ROLE_CLAIM)));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /** Administrators can act on everything, users only on what they own. */
    public boolean canAccess(UUID ownerId) {
        return isAdmin() || id.equals(ownerId);
    }
}
