package com.deploykit.support;

import com.deploykit.security.ProblemSecurityHandler;
import com.deploykit.security.SecurityConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * For {@code @WebMvcTest} controller tests: loads the real security filter chain and authenticates every request as
 * a USER by default (see {@link TestAuthentication}); a test can override that per request.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Import({SecurityConfiguration.class, ProblemSecurityHandler.class, TestAuthentication.class})
@TestPropertySource(properties = "deploykit.security.jwt.secret=" + TestUsers.JWT_SECRET)
public @interface WebMvcSecurity {
}
