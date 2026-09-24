package com.deploykit.dto;

import com.deploykit.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank(message = "must not be blank")
        @Email(message = "must be a valid email address")
        @Size(max = 254, message = "must be at most 254 characters")
        String email,

        // bcrypt only uses the first 72 bytes, so longer passwords are refused instead of silently truncated.
        @NotBlank(message = "must not be blank")
        @Size(min = 12, max = 72, message = "must be between 12 and 72 characters")
        String password,

        @NotNull(message = "must not be null")
        Role role) {

    /** Never print the password, even by accident in a log line. */
    @Override
    public String toString() {
        return "CreateUserRequest[email=" + email + ", password=***, role=" + role + "]";
    }
}
