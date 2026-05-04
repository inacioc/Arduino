package com.example.ordermanagement.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Replaces the real Keycloak JwtDecoder with one that accepts tokens signed
 * with a known test secret. Used in @SpringBootTest tests that make actual
 * HTTP calls (not MockMvc), where SecurityMockMvcRequestPostProcessors is
 * unavailable.
 *
 * For MockMvc tests use: .with(jwt().jwt(...)) — no decoder needed.
 */
@TestConfiguration
@Profile("test")
public class TestSecurityConfig {

    static final String TEST_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hs256";

    /** HMAC-SHA256 decoder that accepts any well-formed test JWT. */
    @Bean
    public JwtDecoder testJwtDecoder() {
        byte[] keyBytes = TEST_SECRET.getBytes(StandardCharsets.UTF_8);
        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
