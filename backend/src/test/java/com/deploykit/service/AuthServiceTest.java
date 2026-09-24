package com.deploykit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.deploykit.domain.Role;
import com.deploykit.domain.User;
import com.deploykit.dto.LoginRequest;
import com.deploykit.dto.LoginResponse;
import com.deploykit.exception.InvalidCredentialsException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.exception.TooManyAttemptsException;
import com.deploykit.mapper.UserMapper;
import com.deploykit.repository.UserRepository;
import com.deploykit.security.JwtService;
import com.deploykit.security.LoginAttemptLimiter;
import com.deploykit.support.TestUsers;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceTest {

    private static final String CLIENT = "10.0.0.1";

    private UserRepository users;
    private PasswordEncoder encoder;
    private JwtService jwt;
    private AuthService service;
    private User alice;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        encoder = mock(PasswordEncoder.class);
        jwt = mock(JwtService.class);
        when(encoder.encode(anyString())).thenReturn("decoy-hash");
        service = new AuthService(users, encoder, jwt, new LoginAttemptLimiter(Clock.systemUTC()), new UserMapper());

        alice = new User("alice@example.com", "alice-hash", Role.USER);
        ReflectionTestUtils.setField(alice, "id", TestUsers.USER_ID);
        ReflectionTestUtils.setField(alice, "createdAt", Instant.parse("2026-09-20T10:00:00Z"));
        when(users.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));
        when(encoder.matches("correct-password", "alice-hash")).thenReturn(true);
        when(jwt.issue(alice)).thenReturn(new JwtService.IssuedToken("signed.jwt.token", Instant.now(), 3600));
    }

    private static LoginRequest login(String email, String password) {
        return new LoginRequest(email, password);
    }

    @Test
    void aCorrectPasswordReturnsATokenAndTheUser() {
        LoginResponse response = service.login(login("alice@example.com", "correct-password"), CLIENT);

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        assertThat(response.user().id()).isEqualTo(TestUsers.USER_ID);
        assertThat(response.user().email()).isEqualTo("alice@example.com");
        assertThat(response.user().role()).isEqualTo(Role.USER);
    }

    @Test
    void theEmailIsTrimmedAndCaseInsensitive() {
        LoginResponse response = service.login(login("  Alice@Example.COM ", "correct-password"), CLIENT);

        assertThat(response.user().email()).isEqualTo("alice@example.com");
    }

    @Test
    void aWrongPasswordIsRefused() {
        assertThatThrownBy(() -> service.login(login("alice@example.com", "wrong"), CLIENT))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(jwt, never()).issue(alice);
    }

    @Test
    void anUnknownEmailIsRefusedWithTheSameErrorAndStillCostsAPasswordCheck() {
        when(users.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(login("ghost@example.com", "whatever"), CLIENT))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        // A hash is compared even though the account does not exist, so the response time does not reveal it.
        verify(encoder).matches("whatever", "decoy-hash");
    }

    @Test
    void repeatedFailuresFromOneClientAreThrottled() {
        for (int i = 0; i < AuthService.MAX_FAILURES_PER_CLIENT; i++) {
            assertThatThrownBy(() -> service.login(login("alice@example.com", "wrong"), CLIENT))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        // Even the right password is refused while blocked, and the account is not looked up again.
        assertThatThrownBy(() -> service.login(login("alice@example.com", "correct-password"), CLIENT))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(users, times(AuthService.MAX_FAILURES_PER_CLIENT)).findByEmail("alice@example.com");
    }

    @Test
    void oneClientCannotLockAnAccountForEveryoneElse() {
        for (int i = 0; i < AuthService.MAX_FAILURES_PER_CLIENT; i++) {
            assertThatThrownBy(() -> service.login(login("alice@example.com", "wrong"), CLIENT))
                    .isInstanceOf(InvalidCredentialsException.class);
        }

        LoginResponse fromAnotherClient = service.login(login("alice@example.com", "correct-password"), "10.0.0.2");

        assertThat(fromAnotherClient.accessToken()).isEqualTo("signed.jwt.token");
    }

    @Test
    void aSuccessfulLoginClearsThePreviousFailures() {
        for (int i = 0; i < AuthService.MAX_FAILURES_PER_CLIENT - 1; i++) {
            assertThatThrownBy(() -> service.login(login("alice@example.com", "wrong"), CLIENT))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
        service.login(login("alice@example.com", "correct-password"), CLIENT);

        for (int i = 0; i < AuthService.MAX_FAILURES_PER_CLIENT - 1; i++) {
            assertThatThrownBy(() -> service.login(login("alice@example.com", "wrong"), CLIENT))
                    .isInstanceOf(InvalidCredentialsException.class);
        }
    }

    @Test
    void meReturnsTheAccount() {
        when(users.findById(TestUsers.USER_ID)).thenReturn(Optional.of(alice));

        assertThat(service.me(TestUsers.USER_ID).email()).isEqualTo("alice@example.com");
    }

    @Test
    void meFailsForAnAccountThatNoLongerExists() {
        when(users.findById(TestUsers.OTHER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.me(TestUsers.OTHER_USER_ID)).isInstanceOf(ResourceNotFoundException.class);
    }
}
