package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Role;
import com.deploykit.dto.CreateUserRequest;
import com.deploykit.exception.InvalidRequestException;
import com.deploykit.security.SecurityProperties;
import com.deploykit.support.TestUsers;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminBootstrapTest {

    private UserService users;

    @BeforeEach
    void setUp() {
        users = mock(UserService.class);
    }

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(users, new SecurityProperties(
                new SecurityProperties.Jwt(TestUsers.JWT_SECRET, "deploykit", Duration.ofMinutes(60)),
                new SecurityProperties.BootstrapAdmin(email, password)));
    }

    @Test
    void createsTheFirstAdministratorWhenThereIsNoUser() {
        when(users.hasUsers()).thenReturn(false);

        bootstrap("admin@example.com", "a-long-admin-password").run(null);

        verify(users).create(new CreateUserRequest("admin@example.com", "a-long-admin-password", Role.ADMIN));
    }

    @Test
    void neverTouchesExistingAccounts() {
        when(users.hasUsers()).thenReturn(true);

        bootstrap("admin@example.com", "a-long-admin-password").run(null);

        verify(users, never()).create(any());
    }

    @Test
    void doesNothingWhenNoAdministratorIsConfigured() {
        when(users.hasUsers()).thenReturn(false);

        bootstrap(null, null).run(null);
        bootstrap("admin@example.com", "").run(null);

        verify(users, never()).create(any());
    }

    @Test
    void aWeakConfiguredPasswordStopsTheStartup() {
        when(users.hasUsers()).thenReturn(false);
        when(users.create(any())).thenThrow(new InvalidRequestException("The password must have at least 12 characters"));

        assertThatThrownBy(() -> bootstrap("admin@example.com", "weak").run(null))
                .isInstanceOf(InvalidRequestException.class);
    }
}
