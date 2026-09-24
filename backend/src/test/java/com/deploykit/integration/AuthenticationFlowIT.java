package com.deploykit.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.deploykit.domain.Role;
import com.deploykit.dto.CreateProjectRequest;
import com.deploykit.dto.CreateUserRequest;
import com.deploykit.dto.LoginRequest;
import com.deploykit.dto.LoginResponse;
import com.deploykit.dto.ProjectResponse;
import com.deploykit.dto.UserResponse;
import com.deploykit.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Everything the service-layer unit tests mock away: real HTTP, the real security filter chain (login issues a
 * real JWT, {@code SecurityConfiguration} verifies it), and a real database enforcing ownership end to end.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthenticationFlowIT extends AbstractPostgresIT {

    private static final String JWT_SECRET = "integration-test-secret-integration-test-secret-32";
    private static final String ADMIN_EMAIL = "admin@integration-test.example.com";
    private static final String ADMIN_PASSWORD = "integration-test-admin-password";

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("deploykit.security.jwt.secret", () -> JWT_SECRET);
        // AdminBootstrap only acts while no user exists yet, so this only ever creates one administrator.
        registry.add("deploykit.security.bootstrap-admin.email", () -> ADMIN_EMAIL);
        registry.add("deploykit.security.bootstrap-admin.password", () -> ADMIN_PASSWORD);
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void theBootstrapAdministratorCanLogInAndCreateAUser() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);

        ResponseEntity<UserResponse> created = rest.exchange("/api/users", HttpMethod.POST,
                new HttpEntity<>(new CreateUserRequest(
                        "carol@integration-test.example.com", "carol-integration-password", Role.USER),
                        authHeaders(adminToken)),
                UserResponse.class);

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().email()).isEqualTo("carol@integration-test.example.com");
    }

    @Test
    void aUserCannotReachSomeoneElsesProjectButCanReuseItsName() {
        String adminToken = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        createUser(adminToken, "amy@integration-test.example.com", "amy-integration-password");
        createUser(adminToken, "zoe@integration-test.example.com", "zoe-integration-password");
        String amyToken = login("amy@integration-test.example.com", "amy-integration-password");
        String zoeToken = login("zoe@integration-test.example.com", "zoe-integration-password");

        ProjectResponse amysProject = createProject(amyToken, "shared-name");

        ResponseEntity<String> zoeReadsAmysProject = rest.exchange(
                "/api/projects/" + amysProject.id(), HttpMethod.GET, new HttpEntity<>(authHeaders(zoeToken)),
                String.class);

        assertThat(zoeReadsAmysProject.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        // The name is free for Zoe: uniqueness is scoped to each owner (V5), not global.
        assertThat(createProject(zoeToken, "shared-name").name()).isEqualTo("shared-name");
    }

    @Test
    void aRequestWithoutATokenIsRejected() {
        ResponseEntity<String> response = rest.getForEntity("/api/projects", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String login(String email, String password) {
        LoginResponse response = rest.postForObject("/api/auth/login", new LoginRequest(email, password),
                LoginResponse.class);
        return response.accessToken();
    }

    private void createUser(String adminToken, String email, String password) {
        rest.exchange("/api/users", HttpMethod.POST,
                new HttpEntity<>(new CreateUserRequest(email, password, Role.USER), authHeaders(adminToken)),
                UserResponse.class);
    }

    private ProjectResponse createProject(String token, String name) {
        ResponseEntity<ProjectResponse> response = rest.exchange("/api/projects", HttpMethod.POST,
                new HttpEntity<>(new CreateProjectRequest(name, "https://github.com/acme/" + name, "main", 8080),
                        authHeaders(token)),
                ProjectResponse.class);
        return response.getBody();
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
