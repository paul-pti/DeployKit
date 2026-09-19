package com.deploykit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deploykit.domain.DeploymentStatus;
import com.deploykit.dto.DeployRequest;
import com.deploykit.dto.DeploymentResponse;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.GlobalExceptionHandler;
import com.deploykit.exception.InvalidRequestException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.exception.ServiceBusyException;
import com.deploykit.service.DeploymentService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DeploymentController.class)
@Import(GlobalExceptionHandler.class)
class DeploymentControllerTest {

    private final UUID projectId = UUID.randomUUID();
    private final UUID deploymentId = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeploymentService deploymentService;

    private DeploymentResponse pending() {
        return new DeploymentResponse(deploymentId, projectId, DeploymentStatus.PENDING,
                "ghcr.io/acme/app:main", null, null, null, Instant.now(), null);
    }

    @Test
    void deployWithoutBodyReturns202WithLocation() throws Exception {
        when(deploymentService.deploy(eq(projectId), any())).thenReturn(pending());

        mockMvc.perform(post("/api/projects/" + projectId + "/deploy"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/deployments/" + deploymentId))
                .andExpect(jsonPath("$.id").value(deploymentId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void deployPassesTheOptionalBodyToTheService() throws Exception {
        when(deploymentService.deploy(eq(projectId), any())).thenReturn(pending());

        mockMvc.perform(post("/api/projects/" + projectId + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"nginx:1.27-alpine\",\"commitSha\":\"abc1234\"}"))
                .andExpect(status().isAccepted());

        ArgumentCaptor<DeployRequest> request = ArgumentCaptor.forClass(DeployRequest.class);
        verify(deploymentService).deploy(eq(projectId), request.capture());
        assertThat(request.getValue()).isEqualTo(new DeployRequest("nginx:1.27-alpine", "abc1234"));
    }

    @Test
    void deployRejectsAnInvalidCommitSha() throws Exception {
        mockMvc.perform(post("/api/projects/" + projectId + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commitSha\":\"not-a-sha\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.commitSha").exists());
    }

    @Test
    void deployReturns404ForAnUnknownProject() throws Exception {
        when(deploymentService.deploy(eq(projectId), any()))
                .thenThrow(new ResourceNotFoundException("Project " + projectId + " not found"));

        mockMvc.perform(post("/api/projects/" + projectId + "/deploy")).andExpect(status().isNotFound());
    }

    @Test
    void deployReturns409WhenADeploymentIsAlreadyInProgress() throws Exception {
        when(deploymentService.deploy(eq(projectId), any()))
                .thenThrow(new ConflictException("A deployment is already in progress for this project"));

        mockMvc.perform(post("/api/projects/" + projectId + "/deploy"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A deployment is already in progress for this project"));
    }

    @Test
    void deployReturns400ForAnInvalidImage() throws Exception {
        when(deploymentService.deploy(eq(projectId), any())).thenThrow(new InvalidRequestException("Invalid image"));

        mockMvc.perform(post("/api/projects/" + projectId + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"image\":\"Not Valid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid image"));
    }

    @Test
    void deployReturns503WhenTheQueueIsFull() throws Exception {
        when(deploymentService.deploy(eq(projectId), any())).thenThrow(new ServiceBusyException("busy"));

        mockMvc.perform(post("/api/projects/" + projectId + "/deploy")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void deployReturns400ForAMalformedProjectId() throws Exception {
        mockMvc.perform(post("/api/projects/not-a-uuid/deploy")).andExpect(status().isBadRequest());
    }

    @Test
    void getReturnsTheDeployment() throws Exception {
        when(deploymentService.get(deploymentId)).thenReturn(pending());

        mockMvc.perform(get("/api/deployments/" + deploymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.image").value("ghcr.io/acme/app:main"));
    }

    @Test
    void getReturns404ForAnUnknownDeployment() throws Exception {
        when(deploymentService.get(deploymentId)).thenThrow(new ResourceNotFoundException("Deployment not found"));

        mockMvc.perform(get("/api/deployments/" + deploymentId)).andExpect(status().isNotFound());
    }
}
