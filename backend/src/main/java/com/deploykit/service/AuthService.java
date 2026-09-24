package com.deploykit.service;

import com.deploykit.domain.User;
import com.deploykit.dto.LoginRequest;
import com.deploykit.dto.LoginResponse;
import com.deploykit.dto.UserResponse;
import com.deploykit.exception.InvalidCredentialsException;
import com.deploykit.exception.ResourceNotFoundException;
import com.deploykit.mapper.UserMapper;
import com.deploykit.repository.UserRepository;
import com.deploykit.security.JwtService;
import com.deploykit.security.LoginAttemptLimiter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    /** Failures allowed per email and client address, and per email alone, within the limiter's window. */
    static final int MAX_FAILURES_PER_CLIENT = 5;
    static final int MAX_FAILURES_PER_EMAIL = 30;

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptLimiter limiter;
    private final UserMapper userMapper;
    /** Checked when the email is unknown, so an unknown email costs as much time as a wrong password. */
    private final String decoyHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
                       LoginAttemptLimiter limiter, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.limiter = limiter;
        this.userMapper = userMapper;
        this.decoyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * @param clientAddress address the request came from, used to throttle guessing without letting one client lock
     *                      an account for everybody else
     */
    public LoginResponse login(LoginRequest request, String clientAddress) {
        String email = normalize(request.email());
        String clientKey = email + "|" + clientAddress;
        limiter.assertAllowed(clientKey, MAX_FAILURES_PER_CLIENT);
        limiter.assertAllowed(email, MAX_FAILURES_PER_EMAIL);

        Optional<User> user = userRepository.findByEmail(email);
        boolean passwordMatches = passwordEncoder.matches(
                request.password(), user.map(User::getPasswordHash).orElse(decoyHash));
        if (user.isEmpty() || !passwordMatches) {
            limiter.recordFailure(clientKey);
            limiter.recordFailure(email);
            log.warn("Failed login attempt");
            throw new InvalidCredentialsException();
        }

        limiter.reset(clientKey);
        limiter.reset(email);
        JwtService.IssuedToken token = jwtService.issue(user.get());
        log.info("User {} logged in", user.get().getId());
        return new LoginResponse(token.value(), "Bearer", token.expiresInSeconds(), userMapper.toResponse(user.get()));
    }

    public UserResponse me(UUID userId) {
        return userRepository.findById(userId)
                .map(userMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User " + userId + " not found"));
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
