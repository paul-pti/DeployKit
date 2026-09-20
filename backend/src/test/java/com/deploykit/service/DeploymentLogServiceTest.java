package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Deployment;
import com.deploykit.domain.DeploymentLog;
import com.deploykit.domain.DeploymentStatus;
import com.deploykit.domain.Environment;
import com.deploykit.domain.LogLevel;
import com.deploykit.domain.Project;
import com.deploykit.dto.DeploymentLogsResponse;
import com.deploykit.dto.PodInfo;
import com.deploykit.exception.KubernetesOperationException;
import com.deploykit.exception.PodLogsUnavailableException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.repository.EnvironmentRepository;
import com.deploykit.repository.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DeploymentLogServiceTest {

    private static final String IMAGE = "nginx:1.27-alpine";
    private static final String NAMESPACE = "dk-demo-a1b2c3";
    private static final String APP = "demo-a1b2c3";

    private final UUID projectId = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000000");
    private final UUID deploymentId = UUID.randomUUID();

    private DeploymentRecorder recorder;
    private ProjectRepository projectRepository;
    private EnvironmentRepository environmentRepository;
    private KubernetesService kubernetes;
    private DeploymentLogService service;
    private Deployment deployment;

    @BeforeEach
    void setUp() {
        recorder = mock(DeploymentRecorder.class);
        projectRepository = mock(ProjectRepository.class);
        environmentRepository = mock(EnvironmentRepository.class);
        kubernetes = mock(KubernetesService.class);
        service = new DeploymentLogService(recorder, projectRepository, environmentRepository, kubernetes);

        Project project = new Project("demo", "https://github.com/acme/demo", "main", 8080);
        ReflectionTestUtils.setField(project, "id", projectId);
        deployment = new Deployment(projectId, 2, IMAGE, null);
        ReflectionTestUtils.setField(deployment, "id", deploymentId);
        deployment.markDeploying(Instant.now());
        deployment.markRunning(Instant.now());

        when(recorder.get(deploymentId)).thenReturn(deployment);
        when(recorder.events(deploymentId)).thenReturn(List.of());
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(environmentRepository.findByProjectIdAndName(projectId, "default"))
                .thenReturn(Optional.of(new Environment(projectId, "default", NAMESPACE)));
    }

    private static PodInfo pod(String name, String image) {
        return new PodInfo(name, "Running", true, 0, null, image, null);
    }

    @Test
    void returnsTheLogsOfThePodsRunningTheDeploymentImageOnly() {
        when(kubernetes.getPods(NAMESPACE, APP)).thenReturn(List.of(pod("web-new", IMAGE), pod("web-old", "nginx:1.26-alpine")));
        when(kubernetes.getPodLogs(NAMESPACE, "web-new", 200, false)).thenReturn("hello\n");

        DeploymentLogsResponse response = service.getLogs(deploymentId, 200, false);

        assertThat(response.deploymentId()).isEqualTo(deploymentId);
        assertThat(response.status()).isEqualTo(DeploymentStatus.RUNNING);
        assertThat(response.podsNote()).isNull();
        assertThat(response.pods()).hasSize(1);
        assertThat(response.pods().get(0).pod()).isEqualTo("web-new");
        assertThat(response.pods().get(0).log()).isEqualTo("hello\n");
        assertThat(response.pods().get(0).error()).isNull();
    }

    @Test
    void includesTheWorkflowEventsOldestFirst() {
        Instant first = Instant.parse("2026-09-20T10:00:00Z");
        Instant second = Instant.parse("2026-09-20T10:00:05Z");
        when(recorder.events(deploymentId)).thenReturn(List.of(
                new DeploymentLog(deploymentId, LogLevel.INFO, "queued", first),
                new DeploymentLog(deploymentId, LogLevel.ERROR, "boom", second)));
        when(kubernetes.getPods(NAMESPACE, APP)).thenReturn(List.of());

        DeploymentLogsResponse response = service.getLogs(deploymentId, 200, false);

        assertThat(response.events()).extracting("message").containsExactly("queued", "boom");
        assertThat(response.events().get(1).level()).isEqualTo(LogLevel.ERROR);
        assertThat(response.events().get(0).timestamp()).isEqualTo(first);
    }

    @Test
    void anOlderDeploymentWhosePodsWereReplacedGetsAnExplanation() {
        when(kubernetes.getPods(NAMESPACE, APP)).thenReturn(List.of(pod("web-newer", "nginx:1.28-alpine")));

        DeploymentLogsResponse response = service.getLogs(deploymentId, 200, false);

        assertThat(response.pods()).isEmpty();
        assertThat(response.podsNote()).contains("newer deployment");
    }

    @Test
    void noPodsAtAllIsExplained() {
        when(kubernetes.getPods(NAMESPACE, APP)).thenReturn(List.of());

        assertThat(service.getLogs(deploymentId, 200, false).podsNote()).contains("No pods exist");
    }

    @Test
    void aDeploymentThatHasNotStartedHasNoPodsAndDoesNotCallKubernetes() {
        Deployment pending = new Deployment(projectId, 3, IMAGE, null);
        UUID pendingId = UUID.randomUUID();
        ReflectionTestUtils.setField(pending, "id", pendingId);
        when(recorder.get(pendingId)).thenReturn(pending);
        when(recorder.events(pendingId)).thenReturn(List.of());

        DeploymentLogsResponse response = service.getLogs(pendingId, 200, false);

        assertThat(response.status()).isEqualTo(DeploymentStatus.PENDING);
        assertThat(response.podsNote()).contains("not started");
        verifyNoInteractions(kubernetes, environmentRepository);
    }

    @Test
    void aProjectNeverDeployedHasNoEnvironment() {
        when(environmentRepository.findByProjectIdAndName(projectId, "default")).thenReturn(Optional.empty());

        DeploymentLogsResponse response = service.getLogs(deploymentId, 200, false);

        assertThat(response.pods()).isEmpty();
        assertThat(response.podsNote()).contains("Nothing has been deployed");
        verifyNoInteractions(kubernetes);
    }

    @Test
    void anUnreachableClusterStillReturnsTheWorkflowEvents() {
        when(recorder.events(deploymentId)).thenReturn(List.of(
                new DeploymentLog(deploymentId, LogLevel.INFO, "queued", Instant.now())));
        when(kubernetes.getPods(NAMESPACE, APP)).thenThrow(new KubernetesOperationException("down", new RuntimeException()));

        DeploymentLogsResponse response = service.getLogs(deploymentId, 200, false);

        assertThat(response.pods()).isEmpty();
        assertThat(response.podsNote()).contains("unreachable");
        assertThat(response.events()).hasSize(1);
    }

    @Test
    void aPodWhoseLogsCannotBeReadIsReportedWithoutHidingTheOthers() {
        when(kubernetes.getPods(NAMESPACE, APP)).thenReturn(List.of(pod("web-a", IMAGE), pod("web-b", IMAGE)));
        when(kubernetes.getPodLogs(NAMESPACE, "web-a", 200, false))
                .thenThrow(new PodLogsUnavailableException("container \"app\" is waiting to start: image can't be pulled"));
        when(kubernetes.getPodLogs(NAMESPACE, "web-b", 200, false)).thenReturn("ok\n");

        DeploymentLogsResponse response = service.getLogs(deploymentId, 200, false);

        assertThat(response.pods()).hasSize(2);
        assertThat(response.pods().get(0).error()).contains("waiting to start");
        assertThat(response.pods().get(0).log()).isEmpty();
        assertThat(response.pods().get(1).log()).isEqualTo("ok\n");
        assertThat(response.pods().get(1).error()).isNull();
    }

    @Test
    void aPodThatVanishedOrFailedToBeReadIsReportedPerPod() {
        when(kubernetes.getPods(NAMESPACE, APP)).thenReturn(List.of(pod("web-a", IMAGE), pod("web-b", IMAGE)));
        when(kubernetes.getPodLogs(NAMESPACE, "web-a", 200, false)).thenThrow(new ResourceNotFoundException("gone"));
        when(kubernetes.getPodLogs(NAMESPACE, "web-b", 200, false))
                .thenThrow(new KubernetesOperationException("boom", new RuntimeException()));

        DeploymentLogsResponse response = service.getLogs(deploymentId, 200, false);

        assertThat(response.pods().get(0).error()).contains("no longer exists");
        assertThat(response.pods().get(1).error()).contains("could not be read");
    }

    @Test
    void tailAndPreviousArePassedToKubernetes() {
        when(kubernetes.getPods(NAMESPACE, APP)).thenReturn(List.of(pod("web-a", IMAGE)));
        when(kubernetes.getPodLogs(NAMESPACE, "web-a", 50, true)).thenReturn("last words\n");

        DeploymentLogsResponse response = service.getLogs(deploymentId, 50, true);

        verify(kubernetes).getPodLogs(NAMESPACE, "web-a", 50, true);
        assertThat(response.pods().get(0).log()).isEqualTo("last words\n");
    }

    @Test
    void carriesThePodStateAlongWithItsLogs() {
        when(kubernetes.getPods(NAMESPACE, APP)).thenReturn(List.of(
                new PodInfo("web-a", "Running", false, 4, "CrashLoopBackOff", IMAGE, null)));
        when(kubernetes.getPodLogs(NAMESPACE, "web-a", 200, false)).thenReturn("");

        DeploymentLogsResponse response = service.getLogs(deploymentId, 200, false);

        assertThat(response.pods().get(0).restarts()).isEqualTo(4);
        assertThat(response.pods().get(0).reason()).isEqualTo("CrashLoopBackOff");
        assertThat(response.pods().get(0).ready()).isFalse();
    }

    @Test
    void anUnknownDeploymentIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(recorder.get(unknown)).thenThrow(new ResourceNotFoundException("Deployment not found"));

        assertThatThrownBy(() -> service.getLogs(unknown, 200, false)).isInstanceOf(ResourceNotFoundException.class);
    }
}
