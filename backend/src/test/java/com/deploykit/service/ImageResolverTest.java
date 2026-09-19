package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deploykit.configuration.DeploymentProperties;
import com.deploykit.domain.ImageReference;
import com.deploykit.domain.Project;
import com.deploykit.dto.DeployRequest;
import com.deploykit.exception.InvalidRequestException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ImageResolverTest {

    private final ImageResolver resolver = new ImageResolver(
            new DeploymentProperties("ghcr.io", Duration.ofMinutes(5), Duration.ofSeconds(2), Duration.ofSeconds(30)));

    private static Project project(String url, String branch) {
        return new Project("demo", url, branch, 8080);
    }

    @Test
    void derivesImageFromRepositoryAndBranchByDefault() {
        ImageReference image = resolver.resolve(project("https://github.com/Acme/My-App.git", "main"), null);

        assertThat(image).hasToString("ghcr.io/acme/my-app:main");
    }

    @Test
    void usesCommitShaAsTagWhenGiven() {
        ImageReference image = resolver.resolve(
                project("https://github.com/acme/app", "main"), new DeployRequest(null, "ABCDEF1234"));

        assertThat(image).hasToString("ghcr.io/acme/app:abcdef1234");
    }

    @Test
    void sanitizesBranchNamesIntoTags() {
        assertThat(resolver.resolve(project("https://github.com/acme/app", "feature/login-page"), null))
                .hasToString("ghcr.io/acme/app:feature-login-page");
        assertThat(resolver.resolve(project("https://github.com/acme/app", "---"), null))
                .hasToString("ghcr.io/acme/app:main");
    }

    @Test
    void explicitImageWinsOverDerivedOne() {
        ImageReference image = resolver.resolve(
                project("https://github.com/acme/app", "main"), new DeployRequest("nginx:1.27-alpine", "abc1234"));

        assertThat(image).hasToString("nginx:1.27-alpine");
    }

    @Test
    void blankImageFallsBackToDerivedOne() {
        ImageReference image = resolver.resolve(
                project("https://github.com/acme/app", "main"), new DeployRequest("  ", null));

        assertThat(image).hasToString("ghcr.io/acme/app:main");
    }

    @Test
    void rejectsInvalidExplicitImage() {
        Project project = project("https://github.com/acme/app", "main");

        assertThatThrownBy(() -> resolver.resolve(project, new DeployRequest("bad@sha256:abc", null)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> resolver.resolve(project, new DeployRequest("Not Valid", null)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsNonGitHubRepositoryWhenImageMustBeDerived() {
        Project project = project("https://gitlab.com/acme/app", "main");

        assertThatThrownBy(() -> resolver.resolve(project, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("GitHub");
    }
}
