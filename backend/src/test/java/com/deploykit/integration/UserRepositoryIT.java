package com.deploykit.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deploykit.domain.Role;
import com.deploykit.domain.User;
import com.deploykit.repository.UserRepository;
import com.deploykit.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@code uq_users_email} is a functional index on {@code lower(email)} (V5): no mock can verify that it actually
 * rejects a case-insensitive duplicate the way a plain unique column would not.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private UserRepository repository;

    @Test
    void emailUniquenessIgnoresCase() {
        repository.saveAndFlush(new User("alice@example.com", "hash", Role.USER));

        assertThatThrownBy(() -> repository.saveAndFlush(new User("ALICE@example.com", "hash", Role.USER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existsByEmailFindsAStoredAddressRegardlessOfCase() {
        repository.saveAndFlush(new User("bob@example.com", "hash", Role.USER));

        assertThat(repository.existsByEmail("bob@example.com")).isTrue();
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
    }
}
