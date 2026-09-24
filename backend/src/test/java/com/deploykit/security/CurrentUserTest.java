package com.deploykit.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.deploykit.domain.Role;
import com.deploykit.support.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

    private static Jwt jwt(String subject, String role) {
        return Jwt.withTokenValue("token").header("alg", "HS256")
                .subject(subject).claim(CurrentUser.ROLE_CLAIM, role).build();
    }

    @Test
    void isReadFromTheTokenClaims() {
        CurrentUser user = CurrentUser.from(jwt(TestUsers.USER_ID.toString(), "USER"));

        assertThat(user).isEqualTo(TestUsers.USER);
        assertThat(user.isAdmin()).isFalse();
        assertThat(CurrentUser.from(jwt(TestUsers.ADMIN_ID.toString(), "ADMIN")).isAdmin()).isTrue();
    }

    @Test
    void aMalformedTokenIsRefused() {
        assertThatThrownBy(() -> CurrentUser.from(jwt("not-a-uuid", "USER"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CurrentUser.from(jwt(TestUsers.USER_ID.toString(), "ROOT")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void usersOnlyAccessWhatTheyOwn() {
        assertThat(TestUsers.USER.canAccess(TestUsers.USER_ID)).isTrue();
        assertThat(TestUsers.USER.canAccess(TestUsers.OTHER_USER_ID)).isFalse();
        // Projects that predate authentication have no owner: a user cannot claim them.
        assertThat(TestUsers.USER.canAccess(null)).isFalse();
    }

    @Test
    void administratorsAccessEverything() {
        assertThat(TestUsers.ADMIN.canAccess(TestUsers.USER_ID)).isTrue();
        assertThat(TestUsers.ADMIN.canAccess(UUID.randomUUID())).isTrue();
        assertThat(TestUsers.ADMIN.canAccess(null)).isTrue();
        assertThat(new CurrentUser(UUID.randomUUID(), Role.ADMIN).isAdmin()).isTrue();
    }
}
