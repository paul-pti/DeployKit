package com.deploykit.service;

import com.deploykit.domain.User;
import com.deploykit.dto.CreateUserRequest;
import com.deploykit.dto.UserResponse;
import com.deploykit.exception.ConflictException;
import com.deploykit.exception.InvalidRequestException;
import com.deploykit.mapper.UserMapper;
import com.deploykit.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    static final int MIN_PASSWORD_LENGTH = 12;
    /** bcrypt ignores everything after 72 bytes, so longer passwords are refused rather than silently truncated. */
    static final int MAX_PASSWORD_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        String email = AuthService.normalize(request.email());
        validatePassword(request.password());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("A user with this email already exists");
        }
        User saved = userRepository.saveAndFlush(
                new User(email, passwordEncoder.encode(request.password()), request.role()));
        log.info("Created {} user {}", saved.getRole(), saved.getId());
        return userMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll(Sort.by("createdAt")).stream().map(userMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public boolean hasUsers() {
        return userRepository.count() > 0;
    }

    /** The password policy is enforced here as well, so accounts created outside the API obey it too. */
    static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new InvalidRequestException(
                    "The password must have at least %d characters and at most %d bytes"
                            .formatted(MIN_PASSWORD_LENGTH, MAX_PASSWORD_BYTES));
        }
    }
}
