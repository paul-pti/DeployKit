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

import com.deploykit.domain.Role;
import com.deploykit.dto.LoginRequest;
import com.deploykit.dto.LoginResponse;
import com.deploykit.dto.UserResponse;
import com.deploykit.exception.GlobalExceptionHandler;
import com.deploykit.exception.InvalidCredentialsException;
import com.deploykit.exception.TooManyAttemptsException;
import com.deploykit.service.AuthService;
import com.deploykit.support.TestUsers;
import com.deploykit.support.WebMvcSecurity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcSecurity
@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    private static final String LOGIN_BODY = "{\"email\":\"user@example.com\",\"password\":\"correct-password\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    private static UserResponse user() {
        return new UserResponse(TestUsers.USER_ID, "user@example.com", Role.USER, Instant.parse("2026-09-20T10:00:00Z"));
    }

    @Test
    void loginReturnsATokenAndTheUser() throws Exception {
        when(authService.login(any(), any())).thenReturn(new LoginResponse("signed.jwt.token", "Bearer", 3600, user()));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("signed.jwt.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.email").value("user@example.com"))
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    void loginPassesTheClientAddressForThrottling() throws Exception {
        when(authService.login(any(), any())).thenReturn(new LoginResponse("t", "Bearer", 1, user()));

        mockMvc.perform(post("/api/auth/login")
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> request = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(request.capture(), eq("203.0.113.9"));
        assertThat(request.getValue().email()).isEqualTo("user@example.com");
    }

    @Test
    void loginRejectsBlankFields() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void wrongCredentialsAre401WithAGenericMessage() throws Exception {
        when(authService.login(any(), any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void tooManyFailuresAre429WithRetryAfter() throws Exception {
        when(authService.login(any(), any())).thenThrow(new TooManyAttemptsException(120));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "120"))
                .andExpect(jsonPath("$.title").value("Too many requests"));
    }

    @Test
    void theLoginRequestNeverPrintsThePassword() {
        assertThat(new LoginRequest("user@example.com", "correct-password").toString())
                .doesNotContain("correct-password").contains("***");
    }

    @Test
    void meReturnsTheAccountBehindTheToken() throws Exception {
        when(authService.me(TestUsers.USER_ID)).thenReturn(user());

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TestUsers.USER_ID.toString()))
                .andExpect(jsonPath("$.role").value("USER"));
    }
}
