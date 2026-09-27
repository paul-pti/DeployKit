package com.deploykit.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deploykit.controller.AuthController;
import com.deploykit.controller.HealthController;
import com.deploykit.controller.ProjectController;
import com.deploykit.controller.UserController;
import com.deploykit.domain.User;
import com.deploykit.dto.LoginResponse;
import com.deploykit.dto.ProjectResponse;
import com.deploykit.dto.UserResponse;
import com.deploykit.service.AuthService;
import com.deploykit.service.ProjectService;
import com.deploykit.service.UserService;
import com.deploykit.support.TestUsers;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Exercises the real filter chain with real tokens: who gets in, who is turned away, and with which status.
 * No default authentication here, every request states what it sends.
 */
@WebMvcTest(controllers = {ProjectController.class, UserController.class, AuthController.class, HealthController.class})
@Import({SecurityConfiguration.class, ProblemSecurityHandler.class, JwtService.class})
@TestPropertySource(properties = "deploykit.security.jwt.secret=" + TestUsers.JWT_SECRET)
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtEncoder encoder;

    @MockitoBean
    private ProjectService projectService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void stubServices() {
        when(projectService.list()).thenReturn(List.of());
        when(userService.list()).thenReturn(List.of());
    }

    private static User account(CurrentUser user) {
        User account = new User(user.role().name().toLowerCase() + "@example.com", "hash", user.role());
        ReflectionTestUtils.setField(account, "id", user.id());
        return account;
    }

    private String token(CurrentUser user) {
        return jwtService.issue(account(user)).value();
    }

    private static SecurityProperties properties(String issuer) {
        return new SecurityProperties(
                new SecurityProperties.Jwt(TestUsers.JWT_SECRET, issuer, Duration.ofMinutes(60)),
                new SecurityProperties.BootstrapAdmin(null, null));
    }

    private ResultActions getWith(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void assertUnauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    @Test
    void requestsWithoutATokenAreRejectedWithAProblemBody() throws Exception {
        assertUnauthorized(mockMvc.perform(get("/api/projects")));
        assertUnauthorized(mockMvc.perform(get("/api/auth/me")));
        assertUnauthorized(mockMvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content("{}")));
    }

    @Test
    void garbageTokensAreRejected() throws Exception {
        assertUnauthorized(getWith("/api/projects", "not-a-jwt"));
        assertUnauthorized(getWith("/api/projects", ""));
    }

    @Test
    void aTokenWithATamperedSignatureIsRejected() throws Exception {
        String valid = token(TestUsers.ADMIN);
        int index = valid.length() - 10;
        char replacement = valid.charAt(index) == 'A' ? 'B' : 'A';
        String tampered = valid.substring(0, index) + replacement + valid.substring(index + 1);

        assertUnauthorized(getWith("/api/users", tampered));
    }

    @Test
    void aTokenWithAForgedRoleIsRejected() throws Exception {
        String[] parts = token(TestUsers.USER).split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"USER\"", "\"ADMIN\"");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        assertUnauthorized(getWith("/api/users", forged));
    }

    @Test
    void anExpiredTokenIsRejected() throws Exception {
        Clock longAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(3)), ZoneOffset.UTC);
        String expired = new JwtService(encoder, properties("deploykit"), longAgo).issue(account(TestUsers.USER)).value();

        assertUnauthorized(getWith("/api/projects", expired));
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() throws Exception {
        String foreign = new JwtService(encoder, properties("someone-else"), Clock.systemUTC())
                .issue(account(TestUsers.USER)).value();

        assertUnauthorized(getWith("/api/projects", foreign));
    }

    @Test
    void anUnsignedTokenIsRejected() throws Exception {
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String header = base64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        String payload = base64.encodeToString(("{\"sub\":\"" + UUID.randomUUID()
                + "\",\"role\":\"ADMIN\",\"iss\":\"deploykit\"}").getBytes(StandardCharsets.UTF_8));

        assertUnauthorized(getWith("/api/users", header + "." + payload + "."));
    }

    @Test
    void aValidTokenOpensTheApi() throws Exception {
        getWith("/api/projects", token(TestUsers.USER)).andExpect(status().isOk());
        getWith("/api/projects", token(TestUsers.ADMIN)).andExpect(status().isOk());
    }

    @Test
    void theUserApiIsReservedToAdministrators() throws Exception {
        getWith("/api/users", token(TestUsers.USER))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));
        getWith("/api/users", token(TestUsers.ADMIN)).andExpect(status().isOk());
    }

    @Test
    void aUserCannotCreateAccountsEither() throws Exception {
        mockMvc.perform(post("/api/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(TestUsers.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"eve@example.com\",\"password\":\"a-long-enough-password\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void healthAndLoginArePublic() throws Exception {
        mockMvc.perform(get("/api/health")).andExpect(status().isOk());

        when(authService.login(any(), any())).thenReturn(new LoginResponse("t", "Bearer", 3600,
                new UserResponse(TestUsers.USER_ID, "user@example.com", TestUsers.USER.role(), Instant.now())));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"whatever\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void actuatorPrometheusAndInfoArePublic() throws Exception {
        // Neither controller is wired into this narrow slice, so a permitAll path 404s (no handler) instead of
        // 401ing (blocked by security) — which is exactly what tells the two apart here.
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/info")).andExpect(status().isNotFound());
    }

    @Test
    void everythingOutsideTheApiIsClosed() throws Exception {
        assertUnauthorized(mockMvc.perform(get("/private")));
        mockMvc.perform(get("/private").header(HttpHeaders.AUTHORIZATION, "Bearer " + token(TestUsers.ADMIN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void writesNeedNoCsrfTokenAndNoSessionIsCreated() throws Exception {
        when(projectService.create(any())).thenReturn(new ProjectResponse(
                UUID.randomUUID(), "demo", "https://github.com/acme/app", "main", 80, Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(TestUsers.USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"demo\",\"repositoryUrl\":\"https://github.com/acme/app\",\"port\":80}"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
