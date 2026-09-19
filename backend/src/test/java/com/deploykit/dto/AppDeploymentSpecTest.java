package com.deploykit.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AppDeploymentSpecTest {

    @Test
    void acceptsValidSpecAndDefaultsEnvToEmpty() {
        AppDeploymentSpec spec = new AppDeploymentSpec("my-ns", "my-app", "nginx:1", 80, 1, null);

        assertThat(spec.env()).isEmpty();
    }

    @Test
    void rejectsInvalidNames() {
        assertThatThrownBy(() -> new AppDeploymentSpec("My_NS", "app", "nginx", 80, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("namespace");
        assertThatThrownBy(() -> new AppDeploymentSpec("ns", "-app", "nginx", 80, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> new AppDeploymentSpec("ns", "a".repeat(64), "nginx", 80, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidImagePortAndReplicas() {
        assertThatThrownBy(() -> new AppDeploymentSpec("ns", "app", " ", 80, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("image");
        assertThatThrownBy(() -> new AppDeploymentSpec("ns", "app", "nginx", 0, 1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("port");
        assertThatThrownBy(() -> new AppDeploymentSpec("ns", "app", "nginx", 80, -1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("replicas");
    }
}
