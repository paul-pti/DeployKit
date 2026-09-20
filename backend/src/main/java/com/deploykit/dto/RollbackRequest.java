package com.deploykit.dto;

import jakarta.validation.constraints.Min;

/**
 * Optional body of a rollback request.
 *
 * @param targetVersion version to restore; when absent, the most recent earlier version that ran successfully with a
 *                      different image is used
 */
public record RollbackRequest(
        @Min(value = 1, message = "must be at least 1")
        Integer targetVersion) {
}
