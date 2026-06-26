package com.example.ordermanagement.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Supplies a dummy {@link JwtDecoder} so {@code SecurityConfig}'s oauth2 resource
 * server can build the filter chain without contacting Keycloak. It is never
 * invoked — {@code SecurityMockMvcRequestPostProcessors.jwt()} injects the
 * SecurityContext directly.
 */
@TestConfiguration
public class WebSecurityTestConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException(
                    "Real JWT decoding is disabled in web-slice tests. Use jwt() instead.");
        };
    }
}
