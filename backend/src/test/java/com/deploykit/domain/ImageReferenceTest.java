package com.deploykit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImageReferenceTest {

    @Test
    void parsesRepositoryAndTag() {
        ImageReference image = ImageReference.parse("ghcr.io/acme/app:abc1234");

        assertThat(image.repository()).isEqualTo("ghcr.io/acme/app");
        assertThat(image.tag()).isEqualTo("abc1234");
        assertThat(image).hasToString("ghcr.io/acme/app:abc1234");
    }

    @Test
    void defaultsTagToLatest() {
        assertThat(ImageReference.parse("nginx")).isEqualTo(new ImageReference("nginx", "latest"));
    }

    @Test
    void registryPortIsNotMistakenForATag() {
        assertThat(ImageReference.parse("localhost:5000/team/app:v1"))
                .isEqualTo(new ImageReference("localhost:5000/team/app", "v1"));
        assertThat(ImageReference.parse("localhost:5000/app"))
                .isEqualTo(new ImageReference("localhost:5000/app", "latest"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "Nginx:1", "a@sha256:abcdef", "repo:", "repo:-bad", "has space:1", "repo:tag with space",
            "repo/:1", "UPPER/case:1"})
    void rejectsInvalidReferences(String reference) {
        assertThatThrownBy(() -> ImageReference.parse(reference)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commitShaTagsAreImmutableEverythingElseIsPulledAlways() {
        assertThat(new ImageReference("acme/app", "abc1234").pullPolicy()).isEqualTo("IfNotPresent");
        assertThat(new ImageReference("acme/app", "0123456789abcdef0123456789abcdef01234567").pullPolicy())
                .isEqualTo("IfNotPresent");
        assertThat(new ImageReference("acme/app", "main").pullPolicy()).isEqualTo("Always");
        assertThat(new ImageReference("acme/app", "latest").pullPolicy()).isEqualTo("Always");
        assertThat(new ImageReference("acme/app", "1.27-alpine").pullPolicy()).isEqualTo("Always");
    }
}
