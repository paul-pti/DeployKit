package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.deploykit.configuration.DeploymentProperties;
import com.deploykit.domain.RolloutState;
import com.deploykit.dto.DeploymentStatusInfo;
import com.deploykit.dto.PodInfo;
import com.deploykit.exception.KubernetesOperationException;
import com.deploykit.exception.ResourceNotFoundException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RolloutMonitorTest {

    private static final String NS = "dk-demo";
    private static final String APP = "demo";

    private KubernetesService kubernetes;
    private List<String> progress;

    @BeforeEach
    void setUp() {
        kubernetes = mock(KubernetesService.class);
        progress = new ArrayList<>();
        when(kubernetes.getPods(NS, APP)).thenReturn(List.of());
    }

    private RolloutMonitor monitor(Duration timeout, Duration grace) {
        return new RolloutMonitor(kubernetes,
                new DeploymentProperties("ghcr.io", timeout, Duration.ofMillis(10), grace));
    }

    private static DeploymentStatusInfo status(RolloutState state, int ready, String message) {
        return new DeploymentStatusInfo(NS, APP, state, 1, ready, ready, ready, message);
    }

    private static PodInfo pod(String reason) {
        return new PodInfo("demo-pod", "Pending", false, 0, reason, null);
    }

    @Test
    void succeedsOnceTheDeploymentIsAvailable() throws Exception {
        when(kubernetes.getDeploymentStatus(NS, APP))
                .thenReturn(status(RolloutState.PROGRESSING, 0, null), status(RolloutState.AVAILABLE, 1, null));

        RolloutResult result = monitor(Duration.ofSeconds(5), Duration.ofSeconds(5)).await(NS, APP, progress::add);

        assertThat(result.success()).isTrue();
        assertThat(progress).containsExactly("Rollout: 0/1 ready, 0 updated", "Rollout: 1/1 ready, 1 updated");
    }

    @Test
    void reportsAFailedRollout() throws Exception {
        when(kubernetes.getDeploymentStatus(NS, APP))
                .thenReturn(status(RolloutState.FAILED, 0, "ProgressDeadlineExceeded"));

        RolloutResult result = monitor(Duration.ofSeconds(5), Duration.ofSeconds(5)).await(NS, APP, progress::add);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Rollout failed").contains("ProgressDeadlineExceeded");
    }

    @Test
    void timesOutWhenTheRolloutNeverCompletes() throws Exception {
        when(kubernetes.getDeploymentStatus(NS, APP)).thenReturn(status(RolloutState.PROGRESSING, 0, null));

        RolloutResult result = monitor(Duration.ofMillis(200), Duration.ofSeconds(5)).await(NS, APP, progress::add);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Timed out").contains("(0/1 ready)");
    }

    @Test
    void failsFastWhenAPodCannotPullItsImage() throws Exception {
        when(kubernetes.getDeploymentStatus(NS, APP)).thenReturn(status(RolloutState.PROGRESSING, 0, null));
        when(kubernetes.getPods(NS, APP)).thenReturn(List.of(pod("ImagePullBackOff")));
        long start = System.nanoTime();

        RolloutResult result = monitor(Duration.ofSeconds(30), Duration.ofMillis(100)).await(NS, APP, progress::add);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).isEqualTo("Pod demo-pod is ImagePullBackOff");
        assertThat(progress).contains("Pod demo-pod is ImagePullBackOff");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void toleratesATransientPodFailureThatRecovers() throws Exception {
        when(kubernetes.getDeploymentStatus(NS, APP)).thenReturn(
                status(RolloutState.PROGRESSING, 0, null),
                status(RolloutState.PROGRESSING, 0, null),
                status(RolloutState.AVAILABLE, 1, null));
        when(kubernetes.getPods(NS, APP)).thenReturn(List.of(pod("ErrImagePull")), List.of(pod(null)));

        RolloutResult result = monitor(Duration.ofSeconds(5), Duration.ofSeconds(5)).await(NS, APP, progress::add);

        assertThat(result.success()).isTrue();
    }

    @Test
    void toleratesADeploymentThatIsNotVisibleYet() throws Exception {
        when(kubernetes.getDeploymentStatus(NS, APP))
                .thenThrow(new ResourceNotFoundException("not yet"))
                .thenReturn(status(RolloutState.AVAILABLE, 1, null));

        RolloutResult result = monitor(Duration.ofSeconds(5), Duration.ofSeconds(5)).await(NS, APP, progress::add);

        assertThat(result.success()).isTrue();
    }

    @Test
    void toleratesTransientApiErrors() throws Exception {
        KubernetesOperationException error = new KubernetesOperationException("boom", new RuntimeException());
        when(kubernetes.getDeploymentStatus(NS, APP))
                .thenThrow(error, error)
                .thenReturn(status(RolloutState.AVAILABLE, 1, null));

        RolloutResult result = monitor(Duration.ofSeconds(5), Duration.ofSeconds(5)).await(NS, APP, progress::add);

        assertThat(result.success()).isTrue();
    }

    @Test
    void givesUpAfterRepeatedApiErrors() throws Exception {
        when(kubernetes.getDeploymentStatus(NS, APP))
                .thenThrow(new KubernetesOperationException("cluster down", new RuntimeException()));

        RolloutResult result = monitor(Duration.ofSeconds(30), Duration.ofSeconds(5)).await(NS, APP, progress::add);

        assertThat(result.success()).isFalse();
        assertThat(result.message()).contains("Lost contact with Kubernetes").contains("cluster down");
    }
}
