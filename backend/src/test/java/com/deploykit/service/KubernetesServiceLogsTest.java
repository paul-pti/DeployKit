package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deploykit.exception.KubernetesOperationException;
import com.deploykit.exception.PodLogsUnavailableException;
import com.deploykit.exception.ResourceNotFoundException;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.EnableKubernetesMockClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Log and failure-handling tests: the CRUD mock does not serve pod logs, so requests are scripted. */
@EnableKubernetesMockClient
class KubernetesServiceLogsTest {

    KubernetesClient client;
    KubernetesMockServer server;

    private KubernetesService service;

    @BeforeEach
    void setUp() {
        service = new KubernetesService(client);
    }

    @Test
    void getPodLogsReturnsLogText() {
        server.expect().get()
                .withPath("/api/v1/namespaces/demo-ns/pods/web-1/log?pretty=false&tailLines=100")
                .andReturn(200, "line one\nline two\n")
                .once();

        assertThat(service.getPodLogs("demo-ns", "web-1", 100)).isEqualTo("line one\nline two\n");
    }

    @Test
    void getPodLogsClampsTailLinesToTheAllowedRange() {
        server.expect().get()
                .withPath("/api/v1/namespaces/demo-ns/pods/web-1/log?pretty=false&tailLines="
                        + KubernetesService.MAX_LOG_LINES)
                .andReturn(200, "ok")
                .once();

        assertThat(service.getPodLogs("demo-ns", "web-1", 1_000_000)).isEqualTo("ok");
    }

    @Test
    void getPodLogsThrowsNotFoundForUnknownPod() {
        server.expect().get()
                .withPath("/api/v1/namespaces/demo-ns/pods/ghost/log?pretty=false&tailLines=50")
                .andReturn(404, "{\"kind\":\"Status\",\"code\":404}")
                .once();

        assertThatThrownBy(() -> service.getPodLogs("demo-ns", "ghost", 50))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void previousLogsReadThePreviousContainer() {
        server.expect().get()
                .withPath("/api/v1/namespaces/demo-ns/pods/web-1/log?pretty=false&previous=true&tailLines=100")
                .andReturn(200, "last words\n")
                .once();

        assertThat(service.getPodLogs("demo-ns", "web-1", 100, true)).isEqualTo("last words\n");
    }

    @Test
    void aContainerThatHasNotStartedIsUnavailableNotAClusterError() {
        String status = "{\"kind\":\"Status\",\"apiVersion\":\"v1\",\"status\":\"Failure\",\"reason\":\"BadRequest\","
                + "\"code\":400,\"message\":\"container \\\"app\\\" in pod \\\"web-1\\\" is waiting to start: "
                + "trying and failing to pull image\"}";
        server.expect().get()
                .withPath("/api/v1/namespaces/demo-ns/pods/web-1/log?pretty=false&tailLines=100")
                .andReturn(400, status)
                .once();

        assertThatThrownBy(() -> service.getPodLogs("demo-ns", "web-1", 100))
                .isInstanceOf(PodLogsUnavailableException.class)
                .hasMessageContaining("waiting to start");
    }

    @Test
    void clusterErrorsAreWrappedInKubernetesOperationException() {
        server.expect().get()
                .withPath("/api/v1/namespaces/demo-ns/pods?labelSelector=app.kubernetes.io%2Fname%3Dweb")
                .andReturn(500, "{\"kind\":\"Status\",\"code\":500}")
                .always(); // the client retries 5xx responses

        assertThatThrownBy(() -> service.getPods("demo-ns", "web"))
                .isInstanceOf(KubernetesOperationException.class)
                .hasMessageContaining("demo-ns/web");
    }
}
