package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Role;
import com.deploykit.domain.User;
import com.deploykit.dto.CreateUserRequest;
import com.deploykit.dto.UserResponse;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.InvalidRequestException;
import com.deploykit.mapper.UserMapper;
import com.deploykit.repository.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private static final String PASSWORD = "a-long-enough-password";

    private UserRepository users;
    private PasswordEncoder encoder;
    private UserService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        service = new UserService(users, encoder, new UserMapper());
        when(encoder.encode(PASSWORD)).thenReturn("{bcrypt}hashed");
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsAUserWithAHashedPasswordAndANormalisedEmail() {
        UserResponse created = service.create(new CreateUserRequest("  Bob@Example.COM ", PASSWORD, Role.USER));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("bob@example.com");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("{bcrypt}hashed").isNotEqualTo(PASSWORD);
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(created.email()).isEqualTo("bob@example.com");
        assertThat(created.role()).isEqualTo(Role.USER);
    }

    @Test
    void aResponseNeverCarriesThePasswordOrItsHash() {
        UserResponse created = service.create(new CreateUserRequest("bob@example.com", PASSWORD, Role.ADMIN));

        assertThat(created.toString()).doesNotContain(PASSWORD).doesNotContain("hashed");
    }

    @Test
    void refusesAnEmailThatIsAlreadyTaken() {
        when(users.existsByEmail("bob@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("Bob@example.com", PASSWORD, Role.USER)))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void refusesAShortPassword() {
        assertThatThrownBy(() -> service.create(new CreateUserRequest("bob@example.com", "short", Role.USER)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("12");
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void refusesAPasswordThatBcryptWouldSilentlyTruncate() {
        String tooLong = "x".repeat(UserService.MAX_PASSWORD_BYTES + 1);
        // 40 two-byte characters are only 40 characters but 80 bytes.
        String tooManyBytes = "é".repeat(40);

        assertThatThrownBy(() -> service.create(new CreateUserRequest("bob@example.com", tooLong, Role.USER)))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.create(new CreateUserRequest("bob@example.com", tooManyBytes, Role.USER)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void acceptsAPasswordAtTheLimits() {
        assertThatCode(() -> UserService.validatePassword("x".repeat(UserService.MIN_PASSWORD_LENGTH)))
                .doesNotThrowAnyException();
        assertThatCode(() -> UserService.validatePassword("x".repeat(UserService.MAX_PASSWORD_BYTES)))
                .doesNotThrowAnyException();
    }

    @Test
    void listsUsersOldestFirst() {
        when(users.findAll(Sort.by("createdAt"))).thenReturn(List.of(
                new User("a@example.com", "h", Role.ADMIN), new User("b@example.com", "h", Role.USER)));

        assertThat(service.list()).extracting(UserResponse::email).containsExactly("a@example.com", "b@example.com");
    }

    @Test
    void knowsWhetherAnyUserExists() {
        when(users.count()).thenReturn(0L, 3L);

        assertThat(service.hasUsers()).isFalse();
        assertThat(service.hasUsers()).isTrue();
    }

    @Test
    void theRequestsToStringHidesThePassword() {
        assertThat(new CreateUserRequest("bob@example.com", PASSWORD, Role.USER).toString())
                .doesNotContain(PASSWORD).contains("***");
    }
}
