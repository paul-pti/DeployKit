package com.deploykit.service;

import com.deploykit.configuration.HelmProperties;
import com.deploykit.configuration.KubernetesProperties;
import com.deploykit.dto.HelmRelease;
import com.deploykit.exception.HelmOperationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Deploys applications with the Helm CLI and the generic chart in helm/deploykit-app. */
@Service
public class HelmService {

    private static final Logger log = LoggerFactory.getLogger(HelmService.class);
    private static final int MAX_SUMMARY_LENGTH = 300;

    private final CommandRunner commandRunner;
    private final HelmProperties helmProperties;
    private final KubernetesProperties kubernetesProperties;

    public HelmService(CommandRunner commandRunner, HelmProperties helmProperties,
                       KubernetesProperties kubernetesProperties) {
        this.commandRunner = commandRunner;
        this.helmProperties = helmProperties;
        this.kubernetesProperties = kubernetesProperties;
    }

    /** Installs the release, or upgrades it in place, and returns Helm's output. */
    public String upgradeInstall(HelmRelease release) {
        Path chart = Path.of(helmProperties.chartPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(chart)) {
            throw new HelmOperationException(
                    "Helm chart not found at " + chart + " (configure deploykit.helm.chart-path)", "");
        }

        List<String> command = new ArrayList<>(List.of(
                helmProperties.binary(), "upgrade", "--install", release.name(), chart.toString(),
                "--namespace", release.namespace(),
                "--set-string", "fullnameOverride=" + release.name(),
                "--set-string", "image.repository=" + release.image().repository(),
                "--set-string", "image.tag=" + release.image().tag(),
                "--set-string", "image.pullPolicy=" + release.image().pullPolicy(),
                "--set", "containerPort=" + release.port()));
        if (StringUtils.hasText(kubernetesProperties.context())) {
            command.add("--kube-context");
            command.add(kubernetesProperties.context());
        }

        log.info("Running helm upgrade --install for release {} in namespace {}", release.name(), release.namespace());
        CommandResult result;
        try {
            result = commandRunner.run(command, helmProperties.timeout());
        } catch (IOException e) {
            throw new HelmOperationException(
                    "Cannot execute '" + helmProperties.binary() + "': " + e.getMessage(), "", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HelmOperationException("Interrupted while running helm", "", e);
        }

        if (result.timedOut()) {
            throw new HelmOperationException("helm timed out after " + helmProperties.timeout(), result.output());
        }
        if (!result.success()) {
            throw new HelmOperationException(
                    "helm exited with code " + result.exitCode() + ": " + lastLine(result.output()), result.output());
        }
        return result.output();
    }

    private static String lastLine(String output) {
        String[] lines = output.strip().split("\\R");
        String last = lines[lines.length - 1].strip();
        return last.length() > MAX_SUMMARY_LENGTH ? last.substring(0, MAX_SUMMARY_LENGTH) + "…" : last;
    }
}
