package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.deploykit.domain.Project;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProjectNamingTest {

    private static final String DNS_1035 = "^[a-z]([-a-z0-9]*[a-z0-9])?$";

    private static Project project(String name, String id) {
        Project project = new Project(name, "https://github.com/acme/app", "main", 8080);
        ReflectionTestUtils.setField(project, "id", UUID.fromString(id));
        return project;
    }

    @Test
    void appNameIsSlugPlusShortId() {
        Project project = project("My Cool App!", "a1b2c3d4-0000-0000-0000-000000000000");

        assertThat(ProjectNaming.appName(project)).isEqualTo("my-cool-app-a1b2c3");
        assertThat(ProjectNaming.namespace(project)).isEqualTo("dk-my-cool-app-a1b2c3");
    }

    @Test
    void namesStartingWithADigitGetALetterPrefix() {
        String name = ProjectNaming.appName(project("123 go", "a1b2c3d4-0000-0000-0000-000000000000"));

        assertThat(name).isEqualTo("p-123-go-a1b2c3").matches(DNS_1035);
    }

    @Test
    void namesWithoutUsableCharactersFallBackToApp() {
        assertThat(ProjectNaming.appName(project("!!!", "a1b2c3d4-0000-0000-0000-000000000000")))
                .isEqualTo("app-a1b2c3");
        assertThat(ProjectNaming.appName(project("Проект", "a1b2c3d4-0000-0000-0000-000000000000")))
                .isEqualTo("app-a1b2c3");
    }

    @Test
    void longNamesAreTruncatedWithinKubernetesLimits() {
        Project project = project("a".repeat(100), "a1b2c3d4-0000-0000-0000-000000000000");

        assertThat(ProjectNaming.appName(project)).hasSizeLessThanOrEqualTo(47).matches(DNS_1035);
        assertThat(ProjectNaming.namespace(project)).hasSizeLessThanOrEqualTo(63).matches(DNS_1035);
    }

    @Test
    void truncationNeverLeavesATrailingDash() {
        String name = "a".repeat(39) + "-b" + "c".repeat(20);

        assertThat(ProjectNaming.slug(name)).hasSize(39).doesNotEndWith("-");
    }

    @Test
    void projectsWhoseNamesSlugifyTheSameStillGetDistinctNames() {
        String first = ProjectNaming.appName(project("my app", "a1b2c3d4-0000-0000-0000-000000000000"));
        String second = ProjectNaming.appName(project("my-app", "ffeedd00-0000-0000-0000-000000000000"));

        assertThat(first).isNotEqualTo(second);
    }
}
