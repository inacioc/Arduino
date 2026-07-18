package com.example.ordermanagement.support;

import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

/**
 * Factory for mock JWT tokens used in integration tests.
 *
 * Uses SecurityMockMvcRequestPostProcessors.jwt() which bypasses the real
 * Keycloak JWKS endpoint — no Keycloak instance needed in tests.
 *
 * The jwt() post-processor injects a fully-authenticated SecurityContext
 * directly, so Spring Security method-level annotations (@PreAuthorize, etc.)
 * work exactly as in production.
 */
public final class JwtHelper {

    private JwtHelper() {}

    public static RequestPostProcessor customerToken(String customerId) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder
                        .subject(customerId)
                        .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                )
                .authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")
                );
    }

    public static RequestPostProcessor adminToken() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder
                        .subject("admin-user")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                )
                .authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")
                );
    }

    public static RequestPostProcessor noToken() {
        // Returns a post-processor that adds NO authentication — triggers 401
        return request -> request;
    }
}
