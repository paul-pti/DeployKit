package com.deploykit.service;

import com.deploykit.domain.RolloutState;
import com.deploykit.dto.AppDeploymentSpec;
import com.deploykit.dto.DeploymentStatusInfo;
import com.deploykit.dto.PodInfo;
import com.deploykit.exception.KubernetesOperationException;
import com.deploykit.exception.ResourceNotFoundException;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.apps.DeploymentStatus;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.net.HttpURLConnection;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The only place that talks to the Kubernetes API. Applications are identified by the
 * {@code app.kubernetes.io/name} label, which the Helm chart in helm/deploykit-app also sets, so pods can be
 * found regardless of whether Helm or this service created the Deployment.
 */
@Service
public class KubernetesService {

    static final String NAME_LABEL = "app.kubernetes.io/name";
    static final String MANAGED_BY_LABEL = "app.kubernetes.io/managed-by";
    static final String MANAGED_BY = "deploykit";
    static final int MAX_LOG_LINES = 5000;

    private static final Logger log = LoggerFactory.getLogger(KubernetesService.class);

    private final KubernetesClient client;

    public KubernetesService(KubernetesClient client) {
        this.client = client;
    }

    public void ensureNamespace(String namespace) {
        call("ensure namespace " + namespace, () -> {
            if (client.namespaces().withName(namespace).get() == null) {
                try {
                    client.namespaces()
                            .resource(new NamespaceBuilder()
                                    .withNewMetadata()
                                    .withName(namespace)
                                    .addToLabels(MANAGED_BY_LABEL, MANAGED_BY)
                                    .endMetadata()
                                    .build())
                            .create();
                    log.info("Created namespace {}", namespace);
                } catch (KubernetesClientException e) {
                    if (e.getCode() != HttpURLConnection.HTTP_CONFLICT) {
                        throw e;
                    }
                    // Created concurrently by someone else: that is fine.
                }
            }
            return null;
        });
    }

    /** Creates the Deployment, or updates it in place when it already exists (server-side apply). */
    public void applyDeployment(AppDeploymentSpec spec) {
        Deployment deployment = buildDeployment(spec);
        call("apply deployment " + spec.namespace() + "/" + spec.name(), () ->
                client.apps().deployments().inNamespace(spec.namespace()).resource(deployment).serverSideApply());
        log.info("Applied deployment {}/{} image={} replicas={}",
                spec.namespace(), spec.name(), spec.image(), spec.replicas());
    }

    Deployment buildDeployment(AppDeploymentSpec spec) {
        Map<String, String> labels = Map.of(NAME_LABEL, spec.name(), MANAGED_BY_LABEL, MANAGED_BY);
        List<EnvVar> env = spec.env().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new EnvVarBuilder().withName(e.getKey()).withValue(e.getValue()).build())
                .toList();

        return new DeploymentBuilder()
                .withNewMetadata()
                    .withName(spec.name())
                    .withNamespace(spec.namespace())
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .withReplicas(spec.replicas())
                    .withNewSelector().addToMatchLabels(NAME_LABEL, spec.name()).endSelector()
                    .withNewTemplate()
                        .withNewMetadata().withLabels(labels).endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("app")
                                .withImage(spec.image())
                                .addNewPort().withName("http").withContainerPort(spec.port()).endPort()
                                .withEnv(env)
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
    }

    public DeploymentStatusInfo getDeploymentStatus(String namespace, String name) {
        Deployment deployment = call("get deployment " + namespace + "/" + name, () ->
                client.apps().deployments().inNamespace(namespace).withName(name).get());
        if (deployment == null) {
            throw new ResourceNotFoundException("Deployment " + namespace + "/" + name + " not found");
        }

        DeploymentStatus status = deployment.getStatus() != null ? deployment.getStatus() : new DeploymentStatus();
        int desired = orZero(deployment.getSpec().getReplicas());
        int total = orZero(status.getReplicas());
        int updated = orZero(status.getUpdatedReplicas());
        int ready = orZero(status.getReadyReplicas());
        int available = orZero(status.getAvailableReplicas());

        List<DeploymentCondition> conditions = status.getConditions() != null ? status.getConditions() : List.of();
        DeploymentCondition progressing = conditions.stream()
                .filter(c -> "Progressing".equals(c.getType()))
                .findFirst()
                .orElse(null);

        boolean deadlineExceeded = progressing != null && "ProgressDeadlineExceeded".equals(progressing.getReason());
        boolean observed = deployment.getMetadata().getGeneration() == null
                || orZero(status.getObservedGeneration()) >= deployment.getMetadata().getGeneration();
        boolean complete = observed && total == desired && updated == desired && ready == desired && available == desired;

        RolloutState state = RolloutState.PROGRESSING;
        if (deadlineExceeded) {
            state = RolloutState.FAILED;
        } else if (complete) {
            state = RolloutState.AVAILABLE;
        }

        return new DeploymentStatusInfo(namespace, name, state, desired, ready, updated, available,
                progressing != null ? progressing.getMessage() : null);
    }

    public List<PodInfo> getPods(String namespace, String name) {
        List<Pod> pods = call("list pods for " + namespace + "/" + name, () ->
                client.pods().inNamespace(namespace).withLabel(NAME_LABEL, name).list().getItems());
        return pods.stream()
                .map(this::toPodInfo)
                .sorted(Comparator.comparing(PodInfo::name))
                .toList();
    }

    public String getPodLogs(String namespace, String podName, int tailLines) {
        int tail = Math.clamp(tailLines, 1, MAX_LOG_LINES);
        String logs = call("get logs of pod " + namespace + "/" + podName, () ->
                client.pods().inNamespace(namespace).withName(podName).tailingLines(tail).getLog());
        return logs == null ? "" : logs;
    }

    private PodInfo toPodInfo(Pod pod) {
        List<ContainerStatus> containers = pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null
                ? pod.getStatus().getContainerStatuses()
                : List.of();

        boolean ready = !containers.isEmpty() && containers.stream().allMatch(c -> Boolean.TRUE.equals(c.getReady()));
        int restarts = containers.stream().mapToInt(c -> orZero(c.getRestartCount())).sum();
        String reason = containers.stream()
                .map(this::containerReason)
                .filter(r -> r != null)
                .findFirst()
                .orElse(pod.getStatus() != null ? pod.getStatus().getReason() : null);
        Instant startedAt = pod.getStatus() != null && pod.getStatus().getStartTime() != null
                ? Instant.parse(pod.getStatus().getStartTime())
                : null;

        return new PodInfo(pod.getMetadata().getName(),
                pod.getStatus() != null ? pod.getStatus().getPhase() : null,
                ready, restarts, reason, startedAt);
    }

    private String containerReason(ContainerStatus container) {
        if (container.getState() == null) {
            return null;
        }
        if (container.getState().getWaiting() != null) {
            return container.getState().getWaiting().getReason();
        }
        if (container.getState().getTerminated() != null) {
            return container.getState().getTerminated().getReason();
        }
        return null;
    }

    /** Runs a Kubernetes call, translating client errors into DeployKit exceptions. */
    private <T> T call(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (KubernetesClientException e) {
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new ResourceNotFoundException("Kubernetes resource not found while trying to " + operation);
            }
            log.error("Kubernetes operation failed: {}", operation, e);
            throw new KubernetesOperationException("Kubernetes operation failed: " + operation, e);
        }
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
