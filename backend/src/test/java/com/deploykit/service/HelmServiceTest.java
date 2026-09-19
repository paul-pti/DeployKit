package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deploykit.configuration.HelmProperties;
import com.deploykit.configuration.KubernetesProperties;
import com.deploykit.domain.ImageReference;
import com.deploykit.dto.HelmRelease;
import com.deploykit.exception.HelmOperationException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class HelmServiceTest {

    private static final HelmRelease RELEASE = new HelmRelease(
            "demo-abc123", "dk-demo-abc123", new ImageReference("ghcr.io/acme/app", "main"), 8080);

    @TempDir
    Path chartDir;

    private CommandRunner runner;

    @BeforeEach
    void setUp() {
        runner = mock(CommandRunner.class);
    }

    private HelmService service(String context) {
        return new HelmService(runner,
                new HelmProperties("helm", chartDir.toString(), Duration.ofMinutes(2)),
                new KubernetesProperties(context));
    }

    @Test
    void buildsTheExpectedUpgradeInstallCommand() throws Exception {
        when(runner.run(anyList(), any())).thenReturn(new CommandResult(0, "Release \"demo\" has been upgraded", false));

        String output = service(null).upgradeInstall(RELEASE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> command = ArgumentCaptor.forClass(List.class);
        verify(runner).run(command.capture(), any());
        assertThat(output).contains("has been upgraded");
        assertThat(command.getValue())
                .containsSequence("helm", "upgrade", "--install", "demo-abc123", chartDir.toAbsolutePath().normalize().toString())
                .containsSequence("--namespace", "dk-demo-abc123")
                .containsSequence("--set-string", "fullnameOverride=demo-abc123")
                .containsSequence("--set-string", "image.repository=ghcr.io/acme/app")
                .containsSequence("--set-string", "image.tag=main")
                .containsSequence("--set-string", "image.pullPolicy=Always")
                .containsSequence("--set", "containerPort=8080")
                .doesNotContain("--kube-context");
    }

    @Test
    void passesTheConfiguredKubeContext() throws Exception {
        when(runner.run(anyList(), any())).thenReturn(new CommandResult(0, "ok", false));

        service("kind-deploykit").upgradeInstall(RELEASE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> command = ArgumentCaptor.forClass(List.class);
        verify(runner).run(command.capture(), any());
        assertThat(command.getValue()).containsSequence("--kube-context", "kind-deploykit");
    }

    @Test
    void nonZeroExitBecomesAnExceptionWithTheOutput() throws Exception {
        when(runner.run(anyList(), any())).thenReturn(new CommandResult(1, "line one\nError: no such host", false));

        assertThatThrownBy(() -> service(null).upgradeInstall(RELEASE))
                .isInstanceOfSatisfying(HelmOperationException.class, e -> {
                    assertThat(e).hasMessageContaining("exited with code 1").hasMessageContaining("no such host");
                    assertThat(e.getOutput()).contains("line one");
                });
    }

    @Test
    void timeoutBecomesAnException() throws Exception {
        when(runner.run(anyList(), any())).thenReturn(new CommandResult(-1, "partial", true));

        assertThatThrownBy(() -> service(null).upgradeInstall(RELEASE))
                .isInstanceOf(HelmOperationException.class)
                .hasMessageContaining("timed out");
    }

    @Test
    void missingHelmBinaryBecomesAnException() throws Exception {
        when(runner.run(anyList(), any())).thenThrow(new IOException("No such file or directory"));

        assertThatThrownBy(() -> service(null).upgradeInstall(RELEASE))
                .isInstanceOf(HelmOperationException.class)
                .hasMessageContaining("Cannot execute 'helm'");
    }

    @Test
    void missingChartFailsBeforeRunningAnything() {
        HelmService service = new HelmService(runner,
                new HelmProperties("helm", chartDir.resolve("nope").toString(), Duration.ofMinutes(2)),
                new KubernetesProperties(null));

        assertThatThrownBy(() -> service.upgradeInstall(RELEASE))
                .isInstanceOf(HelmOperationException.class)
                .hasMessageContaining("chart not found");
        verifyNoInteractions(runner);
    }
}
