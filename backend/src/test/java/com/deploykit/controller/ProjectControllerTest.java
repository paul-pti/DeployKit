package com.deploykit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deploykit.dto.CreateProjectRequest;
import com.deploykit.dto.ProjectResponse;
import com.deploykit.exception.DuplicateResourceException;
import com.deploykit.exception.GlobalExceptionHandler;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.service.ProjectService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.deploykit.support.WebMvcSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcSecurity
@WebMvcTest(ProjectController.class)
@Import(GlobalExceptionHandler.class)
class ProjectControllerTest {

    private static final String VALID_BODY = """
            {"name":"demo","repositoryUrl":"https://github.com/acme/app","branch":"main","port":8080}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProjectService projectService;

    private static ProjectResponse sample(UUID id) {
        return new ProjectResponse(id, "demo", "https://github.com/acme/app", "main", 8080, Instant.now(), Instant.now());
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectService.create(any(CreateProjectRequest.class))).thenReturn(sample(id));

        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/projects/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("demo"));
    }

    @Test
    void createRejectsInvalidPayloadWithFieldErrors() throws Exception {
        String body = """
                {"name":"","repositoryUrl":"https://example.com/x","branch":"main","port":70000}
                """;

        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.repositoryUrl").exists())
                .andExpect(jsonPath("$.errors.port").exists());
    }

    @Test
    void createRejectsMissingPort() throws Exception {
        String body = """
                {"name":"demo","repositoryUrl":"https://github.com/acme/app"}
                """;

        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.port").exists());
    }

    @Test
    void createReturns409OnDuplicateName() throws Exception {
        when(projectService.create(any(CreateProjectRequest.class)))
                .thenThrow(new DuplicateResourceException("A project named 'demo' already exists"));

        mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A project named 'demo' already exists"));
    }

    @Test
    void listReturnsProjects() throws Exception {
        when(projectService.list()).thenReturn(List.of(sample(UUID.randomUUID())));

        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("demo"));
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        when(projectService.get(id)).thenThrow(new ResourceNotFoundException("Project " + id + " not found"));

        mockMvc.perform(get("/api/projects/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not found"));
    }

    @Test
    void getReturns400ForMalformedId() throws Exception {
        mockMvc.perform(get("/api/projects/not-a-uuid")).andExpect(status().isBadRequest());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/projects/" + UUID.randomUUID())).andExpect(status().isNoContent());
    }

    @Test
    void deleteReturns404WhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Project " + id + " not found")).when(projectService).delete(id);

        mockMvc.perform(delete("/api/projects/" + id)).andExpect(status().isNotFound());
    }
}
