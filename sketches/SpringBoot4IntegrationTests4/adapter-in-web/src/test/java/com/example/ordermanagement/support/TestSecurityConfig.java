package com.example.ordermanagement.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;

/**
 * Overrides the JwtDecoder bean for integration tests.
 *
 * Without this, Spring Security tries to fetch the Keycloak JWKS endpoint
 * at context startup (spring.security.oauth2.resourceserver.jwt.jwk-set-uri)
 * causing context load failures when Keycloak is not running.
 *
 * In tests we don't need real JWT validation because:
 *   - SecurityMockMvcRequestPostProcessors.jwt() bypasses the JwtDecoder entirely.
 *   - It injects the SecurityContext directly before the request hits the filter chain.
 *
 * This bean is only active in the "test" profile (see application-test.yml).
 */
@TestConfiguration
public class TestSecurityConfig {

    /**
     * Dummy JwtDecoder that prevents startup failure.
     * It is never actually called because jwt() post-processors bypass it.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        return token -> {
            throw new UnsupportedOperationException(
                    "Real JWT decoding is disabled in integration tests. " +
                    "Use SecurityMockMvcRequestPostProcessors.jwt() instead.");
        };
    }
}
