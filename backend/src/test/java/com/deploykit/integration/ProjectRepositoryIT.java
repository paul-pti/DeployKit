package com.deploykit.integration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deploykit.domain.Project;
import com.deploykit.domain.Role;
import com.deploykit.domain.User;
import com.deploykit.repository.ProjectRepository;
import com.deploykit.repository.UserRepository;
import com.deploykit.support.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * {@code uq_projects_owner_name} (V5) scopes name uniqueness per owner through
 * {@code COALESCE(owner_id, '0000...'::uuid)}: an expression index that only a real database evaluates.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private UserRepository users;

    @Test
    void twoOwnersCanEachHaveAProjectCalledTheSame() {
        User alice = users.saveAndFlush(new User("alice@example.com", "hash", Role.USER));
        User bob = users.saveAndFlush(new User("bob@example.com", "hash", Role.USER));
        projects.saveAndFlush(new Project("demo", "https://github.com/acme/demo", "main", 8080, alice.getId()));

        assertThatCode(() -> projects.saveAndFlush(
                        new Project("demo", "https://github.com/acme/demo", "main", 8080, bob.getId())))
                .doesNotThrowAnyException();
    }

    @Test
    void oneOwnerCannotHaveTwoProjectsCalledTheSame() {
        User alice = users.saveAndFlush(new User("alice@example.com", "hash", Role.USER));
        projects.saveAndFlush(new Project("demo", "https://github.com/acme/demo", "main", 8080, alice.getId()));

        assertThatThrownBy(() -> projects.saveAndFlush(
                        new Project("demo", "https://github.com/acme/other", "release", 9090, alice.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void twoOwnerlessLegacyProjectsCannotShareAName() {
        projects.saveAndFlush(new Project("legacy", "https://github.com/acme/a", "main", 80));

        assertThatThrownBy(() ->
                        projects.saveAndFlush(new Project("legacy", "https://github.com/acme/b", "main", 81)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
