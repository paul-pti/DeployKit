package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Uses standard POSIX tools (echo, sh, sleep). */
@DisabledOnOs(OS.WINDOWS)
class ProcessCommandRunnerTest {

    private final ProcessCommandRunner runner = new ProcessCommandRunner();

    @Test
    void capturesOutputAndExitCode() throws Exception {
        CommandResult result = runner.run(List.of("echo", "hello"), Duration.ofSeconds(5));

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("hello\n");
    }

    @Test
    void mergesStderrAndReportsNonZeroExit() throws Exception {
        CommandResult result = runner.run(List.of("sh", "-c", "echo out; echo err 1>&2; exit 3"), Duration.ofSeconds(5));

        assertThat(result.exitCode()).isEqualTo(3);
        assertThat(result.success()).isFalse();
        assertThat(result.output()).contains("out").contains("err");
    }

    @Test
    void killsTheProcessWhenTheTimeoutExpires() throws Exception {
        long start = System.nanoTime();

        CommandResult result = runner.run(List.of("sleep", "10"), Duration.ofMillis(300));

        assertThat(result.timedOut()).isTrue();
        assertThat(result.success()).isFalse();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void reportsAMissingBinaryAsIoException() {
        assertThatThrownBy(() -> runner.run(List.of("definitely-not-a-real-binary-xyz"), Duration.ofSeconds(5)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void doesNotInterpretShellMetacharacters() throws Exception {
        CommandResult result = runner.run(List.of("echo", "$HOME; rm -rf / `whoami`"), Duration.ofSeconds(5));

        assertThat(result.output()).isEqualTo("$HOME; rm -rf / `whoami`\n");
    }
}
