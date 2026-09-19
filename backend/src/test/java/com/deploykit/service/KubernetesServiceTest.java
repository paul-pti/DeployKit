package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deploykit.domain.RolloutState;
import com.deploykit.dto.AppDeploymentSpec;
import com.deploykit.dto.DeploymentStatusInfo;
import com.deploykit.dto.PodInfo;
import com.deploykit.exception.ResourceNotFoundException;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@EnableKubernetesMockClient(crud = true)
class KubernetesServiceTest {

    KubernetesClient client;

    private KubernetesService service;

    @BeforeEach
    void setUp() {
        service = new KubernetesService(client);
    }

    private static AppDeploymentSpec spec(String image, int replicas) {
        return new AppDeploymentSpec("demo-ns", "web", image, 8080, replicas, Map.of("B", "2", "A", "1"));
    }

    @Test
    void ensureNamespaceCreatesItOnceAndIsIdempotent() {
        service.ensureNamespace("demo-ns");
        service.ensureNamespace("demo-ns");

        assertThat(client.namespaces().list().getItems()).hasSize(1);
        assertThat(client.namespaces().withName("demo-ns").get().getMetadata().getLabels())
                .containsEntry("app.kubernetes.io/managed-by", "deploykit");
    }

    /**
     * The mock API server does not implement server-side apply, so the apply call itself is covered by
     * KubernetesServiceClusterTest against a real cluster; here we verify the object that is sent.
     */
    @Test
    void buildDeploymentProducesExpectedShape() {
        Deployment deployment = service.buildDeployment(spec("nginx:1.27", 2));

        assertThat(deployment.getMetadata().getName()).isEqualTo("web");
        assertThat(deployment.getMetadata().getNamespace()).isEqualTo("demo-ns");
        assertThat(deployment.getSpec().getReplicas()).isEqualTo(2);
        assertThat(deployment.getSpec().getSelector().getMatchLabels()).containsEntry("app.kubernetes.io/name", "web");
        assertThat(deployment.getSpec().getTemplate().getMetadata().getLabels())
                .containsEntry("app.kubernetes.io/name", "web")
                .containsEntry("app.kubernetes.io/managed-by", "deploykit");
        var container = deployment.getSpec().getTemplate().getSpec().getContainers().get(0);
        assertThat(container.getImage()).isEqualTo("nginx:1.27");
        assertThat(container.getPorts().get(0).getContainerPort()).isEqualTo(8080);
        assertThat(container.getEnv()).extracting("name").containsExactly("A", "B");
    }

    @Test
    void deleteNamespaceRemovesANamespaceCreatedByDeployKit() {
        service.ensureNamespace("demo-ns");

        service.deleteNamespace("demo-ns");

        assertThat(client.namespaces().withName("demo-ns").get()).isNull();
    }

    @Test
    void deleteNamespaceRefusesToTouchNamespacesItDidNotCreate() {
        client.namespaces().resource(new NamespaceBuilder().withNewMetadata().withName("kube-foreign").endMetadata().build())
                .create();

        service.deleteNamespace("kube-foreign");

        assertThat(client.namespaces().withName("kube-foreign").get()).isNotNull();
    }

    @Test
    void deleteNamespaceIgnoresAMissingNamespace() {
        service.deleteNamespace("does-not-exist");
    }

    @Test
    void getDeploymentStatusThrowsWhenMissing() {
        assertThatThrownBy(() -> service.getDeploymentStatus("demo-ns", "ghost"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void createDeployment(int desired, int ready, String conditionReason) {
        var builder = new DeploymentBuilder()
                .withNewMetadata().withName("web").withNamespace("demo-ns").withGeneration(2L).endMetadata()
                .withNewSpec().withReplicas(desired).endSpec()
                .withNewStatus()
                    .withObservedGeneration(2L)
                    .withReplicas(desired)
                    .withUpdatedReplicas(ready)
                    .withReadyReplicas(ready)
                    .withAvailableReplicas(ready);
        if (conditionReason != null) {
            builder.addNewCondition()
                    .withType("Progressing").withStatus("False").withReason(conditionReason).withMessage("msg")
                    .endCondition();
        }
        client.apps().deployments().inNamespace("demo-ns").resource(builder.endStatus().build()).create();
    }

    @Test
    void statusIsAvailableWhenAllReplicasAreReady() {
        createDeployment(2, 2, null);

        DeploymentStatusInfo status = service.getDeploymentStatus("demo-ns", "web");

        assertThat(status.state()).isEqualTo(RolloutState.AVAILABLE);
        assertThat(status.desiredReplicas()).isEqualTo(2);
        assertThat(status.readyReplicas()).isEqualTo(2);
    }

    @Test
    void statusIsProgressingWhenReplicasAreNotReadyYet() {
        createDeployment(2, 1, null);

        assertThat(service.getDeploymentStatus("demo-ns", "web").state()).isEqualTo(RolloutState.PROGRESSING);
    }

    @Test
    void statusIsFailedWhenProgressDeadlineExceeded() {
        createDeployment(2, 0, "ProgressDeadlineExceeded");

        DeploymentStatusInfo status = service.getDeploymentStatus("demo-ns", "web");

        assertThat(status.state()).isEqualTo(RolloutState.FAILED);
        assertThat(status.message()).isEqualTo("msg");
    }

    private Pod pod(String name, String appName) {
        return new PodBuilder()
                .withNewMetadata().withName(name).withNamespace("demo-ns")
                    .addToLabels("app.kubernetes.io/name", appName).endMetadata()
                .build();
    }

    @Test
    void getPodsMapsHealthyAndFailingPodsAndFiltersByApp() {
        Pod healthy = new PodBuilder(pod("web-b", "web"))
                .withNewStatus().withPhase("Running").withStartTime("2026-09-20T10:00:00Z")
                    .addNewContainerStatus().withName("app").withReady(true).withRestartCount(0)
                        .withNewState().withNewRunning().endRunning().endState()
                    .endContainerStatus()
                .endStatus().build();
        Pod failing = new PodBuilder(pod("web-a", "web"))
                .withNewStatus().withPhase("Pending")
                    .addNewContainerStatus().withName("app").withReady(false).withRestartCount(3)
                        .withNewState().withNewWaiting().withReason("ImagePullBackOff").endWaiting().endState()
                    .endContainerStatus()
                .endStatus().build();
        Pod other = pod("other-1", "other");
        client.pods().inNamespace("demo-ns").resource(healthy).create();
        client.pods().inNamespace("demo-ns").resource(failing).create();
        client.pods().inNamespace("demo-ns").resource(other).create();

        List<PodInfo> pods = service.getPods("demo-ns", "web");

        assertThat(pods).extracting(PodInfo::name).containsExactly("web-a", "web-b");
        assertThat(pods.get(0).ready()).isFalse();
        assertThat(pods.get(0).reason()).isEqualTo("ImagePullBackOff");
        assertThat(pods.get(0).restarts()).isEqualTo(3);
        assertThat(pods.get(1).ready()).isTrue();
        assertThat(pods.get(1).reason()).isNull();
        assertThat(pods.get(1).startedAt()).isNotNull();
    }

    @Test
    void getPodsReturnsEmptyListWhenNoPods() {
        assertThat(service.getPods("demo-ns", "web")).isEmpty();
    }
}
