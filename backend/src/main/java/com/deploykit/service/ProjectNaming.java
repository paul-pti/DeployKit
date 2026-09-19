package com.deploykit.service;

import com.deploykit.domain.Project;
import java.util.Locale;

/**
 * Derives Kubernetes-safe names from a project. The name is a slug of the project name plus the first six
 * characters of its id, so two projects whose names slugify identically still get distinct resources.
 */
public final class ProjectNaming {

    public static final String DEFAULT_ENVIRONMENT = "default";

    static final int MAX_SLUG_LENGTH = 40;
    private static final String NAMESPACE_PREFIX = "dk-";

    private ProjectNaming() {
    }

    /** Helm release name and Kubernetes application name (at most 47 characters, DNS-1035 compatible). */
    public static String appName(Project project) {
        return slug(project.getName()) + "-" + project.getId().toString().substring(0, 6);
    }

    public static String namespace(Project project) {
        return NAMESPACE_PREFIX + appName(project);
    }

    static String slug(String name) {
        String slug = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }
        if (slug.isEmpty()) {
            return "app";
        }
        // Kubernetes Service names must start with a letter.
        return Character.isLetter(slug.charAt(0)) ? slug : "p-" + slug;
    }
}
