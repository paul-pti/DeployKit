package com.deploykit.dto;

import com.deploykit.domain.ImageReference;

/** What to install or upgrade with Helm: the release/app name, its namespace, the image and container port. */
public record HelmRelease(String name, String namespace, ImageReference image, int port) {
}
