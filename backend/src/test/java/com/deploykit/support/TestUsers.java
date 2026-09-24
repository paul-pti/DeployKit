package com.deploykit.support;

import com.deploykit.domain.Role;
import com.deploykit.security.CurrentUser;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Fixed identities and helpers shared by the security-aware tests. */
public final class TestUsers {

    public static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final UUID OTHER_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID ADMIN_ID = UUID.fromString("99999999-9999-9999-9999-999999999999");

    public static final CurrentUser USER = new CurrentUser(USER_ID, Role.USER);
    public static final CurrentUser OTHER_USER = new CurrentUser(OTHER_USER_ID, Role.USER);
    public static final CurrentUser ADMIN = new CurrentUser(ADMIN_ID, Role.ADMIN);

    /** A 40-character signing secret, long enough for HS256, used only by tests. */
    public static final String JWT_SECRET = "test-secret-test-secret-test-secret-0123456789";

    private TestUsers() {
    }

    /** Authenticates a MockMvc request as the given user, with the same claims a real token carries. */
    public static RequestPostProcessor authenticatedAs(CurrentUser user) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(token -> token
                        .subject(user.id().toString())
                        .claim(CurrentUser.EMAIL_CLAIM, user.role().name().toLowerCase() + "@example.com")
                        .claim(CurrentUser.ROLE_CLAIM, user.role().name()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.role().name()));
    }
}
