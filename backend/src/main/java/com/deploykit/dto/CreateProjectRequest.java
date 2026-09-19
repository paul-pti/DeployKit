package com.deploykit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "must not be blank")
        @Size(max = 100, message = "must be at most 100 characters")
        String name,

        @NotBlank(message = "must not be blank")
        @Size(max = 500, message = "must be at most 500 characters")
        @Pattern(
                regexp = "^https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?(\\.git)?/?$",
                message = "must be a GitHub repository URL like https://github.com/owner/repo")
        String repositoryUrl,

        @Size(max = 255, message = "must be at most 255 characters")
        @Pattern(regexp = "^[A-Za-z0-9._/-]*$", message = "contains invalid characters")
        String branch,

        @NotNull(message = "must not be null")
        @Min(value = 1, message = "must be between 1 and 65535")
        @Max(value = 65535, message = "must be between 1 and 65535")
        Integer port) {
}
