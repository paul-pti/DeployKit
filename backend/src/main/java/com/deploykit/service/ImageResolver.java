package com.deploykit.service;

import com.deploykit.configuration.DeploymentProperties;
import com.deploykit.domain.ImageReference;
import com.deploykit.domain.Project;
import com.deploykit.dto.DeployRequest;
import com.deploykit.exception.InvalidRequestException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Decides which container image to deploy: the one given in the request, otherwise the image the build
 * pipeline publishes for the project: {@code <registry>/<owner>/<repo>:<commit sha or branch>}.
 */
@Component
public class ImageResolver {

    private static final Pattern GITHUB_URL =
            Pattern.compile("^https://github\\.com/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+?)(?:\\.git)?/?$");
    private static final int MAX_TAG_LENGTH = 128;

    private final DeploymentProperties properties;

    public ImageResolver(DeploymentProperties properties) {
        this.properties = properties;
    }

    public ImageReference resolve(Project project, DeployRequest request) {
        try {
            if (request != null && StringUtils.hasText(request.image())) {
                return ImageReference.parse(request.image());
            }
            return new ImageReference(derivedRepository(project), derivedTag(project, request));
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException(e.getMessage());
        }
    }

    private String derivedRepository(Project project) {
        Matcher matcher = GITHUB_URL.matcher(project.getRepositoryUrl());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Project repository is not a GitHub URL: " + project.getRepositoryUrl());
        }
        // Registries require lowercase repository names.
        return (properties.registry() + "/" + matcher.group(1) + "/" + matcher.group(2)).toLowerCase(Locale.ROOT);
    }

    private String derivedTag(Project project, DeployRequest request) {
        if (request != null && StringUtils.hasText(request.commitSha())) {
            return request.commitSha().toLowerCase(Locale.ROOT);
        }
        String tag = project.getBranch()
                .replaceAll("[^A-Za-z0-9_.-]", "-")
                .replaceAll("^[.-]+", "");
        if (tag.length() > MAX_TAG_LENGTH) {
            tag = tag.substring(0, MAX_TAG_LENGTH);
        }
        return tag.isEmpty() ? "main" : tag;
    }
}
