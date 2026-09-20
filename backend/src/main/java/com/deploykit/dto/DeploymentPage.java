package com.deploykit.dto;

import java.util.List;

/** One page of a project's deployment history, newest first. {@code page} is zero-based. */
public record DeploymentPage(
        List<DeploymentResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
