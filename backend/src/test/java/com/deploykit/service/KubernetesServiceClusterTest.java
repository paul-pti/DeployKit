package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.deploykit.domain.RolloutState;
import com.deploykit.dto.AppDeploymentSpec;
import com.deploykit.dto.DeploymentStatusInfo;
import com.deploykit.dto.PodInfo;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Runs against the cluster of the current kubeconfig context (see infrastructure/kind).
 * Opt-in: DEPLOYKIT_IT_K8S=true ./mvnw test -Dtest=KubernetesServiceClusterTest
 */
@EnabledIfEnvironmentVariable(named = "DEPLOYKIT_IT_K8S", matches = "true")
class KubernetesServiceClusterTest {

    private static final String NAMESPACE = "deploykit-it";
    private static final String APP = "web";

    private static KubernetesClient client;
    private static KubernetesService service;

    @BeforeAll
    static void connect() {
        client = new KubernetesClientBuilder().build();
        service = new KubernetesService(client);
    }

    @AfterAll
    static void cleanUp() {
        client.namespaces().withName(NAMESPACE).delete();
        client.close();
    }

    private static <T> T await(Supplier<T> supplier, Predicate<T> done, String what) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(180).toNanos();
        T value = supplier.get();
        while (!done.test(value)) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out waiting for " + what + ", last value: " + value);
            }
            Thread.sleep(2000);
            value = supplier.get();
        }
        return value;
    }

    @Test
    void deploysScalesAndReadsStatusPodsAndLogs() throws Exception {
        service.ensureNamespace(NAMESPACE);
        service.applyDeployment(new AppDeploymentSpec(
                NAMESPACE, APP, "nginx:1.27-alpine", 80, 1, Map.of("GREETING", "hello")));

        DeploymentStatusInfo status = await(() -> service.getDeploymentStatus(NAMESPACE, APP),
                s -> s.state() == RolloutState.AVAILABLE, "deployment to become AVAILABLE");
        assertThat(status.readyReplicas()).isEqualTo(1);

        List<PodInfo> pods = service.getPods(NAMESPACE, APP);
        assertThat(pods).hasSize(1);
        assertThat(pods.get(0).ready()).isTrue();
        assertThat(pods.get(0).phase()).isEqualTo("Running");
        assertThat(service.getPodLogs(NAMESPACE, pods.get(0).name(), 50)).isNotBlank();

        // Update in place: scale to 2 replicas.
        service.applyDeployment(new AppDeploymentSpec(
                NAMESPACE, APP, "nginx:1.27-alpine", 80, 2, Map.of("GREETING", "hello")));
        await(() -> service.getDeploymentStatus(NAMESPACE, APP),
                s -> s.state() == RolloutState.AVAILABLE && s.readyReplicas() == 2, "2 ready replicas");
        assertThat(service.getPods(NAMESPACE, APP)).hasSize(2);
    }
}
