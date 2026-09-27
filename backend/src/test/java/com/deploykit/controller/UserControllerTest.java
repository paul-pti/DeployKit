package com.deploykit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.deploykit.domain.Role;
import com.deploykit.dto.CreateUserRequest;
import com.deploykit.dto.UserResponse;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.GlobalExceptionHandler;
import com.deploykit.exception.InvalidRequestException;
import com.deploykit.service.UserService;
import com.deploykit.support.TestUsers;
import com.deploykit.support.WebMvcSecurity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcSecurity
@WebMvcTest(UserController.class)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

    private static final String VALID_BODY =
            "{\"email\":\"bob@example.com\",\"password\":\"a-long-enough-password\",\"role\":\"USER\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    private static UserResponse bob() {
        return new UserResponse(UUID.randomUUID(), "bob@example.com", Role.USER, Instant.parse("2026-09-20T10:00:00Z"));
    }

    @Test
    void anAdministratorListsTheUsers() throws Exception {
        when(userService.list()).thenReturn(List.of(bob()));

        mockMvc.perform(get("/api/users").with(TestUsers.authenticatedAs(TestUsers.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("bob@example.com"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }

    @Test
    void anAdministratorCreatesAUser() throws Exception {
        when(userService.create(any())).thenReturn(bob());

        mockMvc.perform(post("/api/users").with(TestUsers.authenticatedAs(TestUsers.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("bob@example.com"))
                .andExpect(jsonPath("$.role").value("USER"));

        ArgumentCaptor<CreateUserRequest> request = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService).create(request.capture());
        assertThat(request.getValue().role()).isEqualTo(Role.USER);
    }

    @Test
    void createRejectsInvalidInput() throws Exception {
        String invalid = "{\"email\":\"not-an-email\",\"password\":\"short\"}";

        mockMvc.perform(post("/api/users").with(TestUsers.authenticatedAs(TestUsers.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.role").exists());
        verifyNoInteractions(userService);
    }

    @Test
    void createRejectsAnUnknownRole() throws Exception {
        String unknownRole = "{\"email\":\"bob@example.com\",\"password\":\"a-long-enough-password\",\"role\":\"ROOT\"}";

        mockMvc.perform(post("/api/users").with(TestUsers.authenticatedAs(TestUsers.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(unknownRole))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(userService);
    }

    @Test
    void createReturns409ForAnEmailAlreadyInUse() throws Exception {
        when(userService.create(any())).thenThrow(new ConflictException("A user with this email already exists"));

        mockMvc.perform(post("/api/users").with(TestUsers.authenticatedAs(TestUsers.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A user with this email already exists"));
    }

    @Test
    void createReturns400WhenThePasswordPolicyRejectsIt() throws Exception {
        when(userService.create(any())).thenThrow(new InvalidRequestException("The password must have at least 12 characters"));

        mockMvc.perform(post("/api/users").with(TestUsers.authenticatedAs(TestUsers.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aPlainUserIsForbidden() throws Exception {
        mockMvc.perform(get("/api/users")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userService);
    }
}
