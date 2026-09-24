package com.deploykit.support;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Makes every MockMvc request of a controller test come from an authenticated USER unless it says otherwise. */
@TestConfiguration(proxyBeanMethods = false)
public class TestAuthentication {

    @Bean
    MockMvcBuilderCustomizer defaultAuthenticatedUser() {
        return builder -> builder.defaultRequest(
                MockMvcRequestBuilders.get("/").with(TestUsers.authenticatedAs(TestUsers.USER)));
    }
}
