package com.deploykit.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Optional body of a deploy request.
 *
 * @param image     full image reference to deploy (repository[:tag]); overrides the derived image
 * @param commitSha commit the image was built from; used as the image tag when {@code image} is absent
 */
public record DeployRequest(
        @Size(max = 500, message = "must be at most 500 characters")
        String image,

        @Pattern(regexp = "^[0-9a-fA-F]{7,40}$", message = "must be a 7 to 40 character hexadecimal commit SHA")
        String commitSha) {
}
