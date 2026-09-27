package com.deploykit.service;

import com.deploykit.domain.Role;
import com.deploykit.dto.CreateUserRequest;
import com.deploykit.security.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator at startup, from the environment, and only while no user exists. Without it nobody
 * could log in to create the other accounts. Later restarts never touch existing accounts, so changing the
 * environment variables afterwards has no effect on the stored password.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserService userService;
    private final SecurityProperties.BootstrapAdmin settings;

    public AdminBootstrap(UserService userService, SecurityProperties properties) {
        this.userService = userService;
        this.settings = properties.bootstrapAdmin();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userService.hasUsers()) {
            return;
        }
        if (!settings.isConfigured()) {
            log.warn("There is no user yet and no bootstrap administrator is configured: set DEPLOYKIT_ADMIN_EMAIL and "
                    + "DEPLOYKIT_ADMIN_PASSWORD (at least 12 characters) and restart to create the first administrator");
            return;
        }
        userService.create(new CreateUserRequest(settings.email(), settings.password(), Role.ADMIN));
        log.info("Created the bootstrap administrator from the environment");
    }
}
