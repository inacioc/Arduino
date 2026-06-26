package com.example.ordermanagement.support;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.Map;

/**
 * Factory for mock JWT tokens used in the web-slice tests.
 * Uses {@code jwt()} which bypasses the real Keycloak JWKS endpoint.
 */
public final class JwtHelper {

    private JwtHelper() {}

    public static RequestPostProcessor customerToken(String customerId) {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder
                        .subject(customerId)
                        .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                )
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    public static RequestPostProcessor adminToken() {
        return SecurityMockMvcRequestPostProcessors.jwt()
                .jwt(builder -> builder
                        .subject("admin-user")
                        .claim("realm_access", Map.of("roles", List.of("ADMIN")))
                )
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
