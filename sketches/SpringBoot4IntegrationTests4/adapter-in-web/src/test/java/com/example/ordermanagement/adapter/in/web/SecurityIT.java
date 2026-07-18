package com.example.ordermanagement.adapter.in.web;

import com.example.ordermanagement.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests that document the security rules enforced at the HTTP layer.
 *
 * Keycloak integration approach:
 *  - In production: Spring Security validates JWT signature against Keycloak JWKS.
 *  - In tests: SecurityMockMvcRequestPostProcessors.jwt() bypasses JWKS validation
 *    and injects a mock SecurityContext directly.
 *  - The SecurityConfig.keycloakJwtConverter() maps realm_access.roles → ROLE_XXX.
 *  - Tests reproduce the same claim structure Keycloak emits.
 *
 * This means: no Keycloak server, no token endpoint calls, no JWKS endpoint calls.
 */
class SecurityIT extends IntegrationTestBase {

    // ── Unauthenticated access ────────────────────────────────────────────────

    @Test
    @DisplayName("Any endpoint returns 401 with no Authorization header")
    void anyEndpoint_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/orders/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    // ── Role-based access ─────────────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN role can access admin-only endpoints")
    void adminRole_canAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(jwt()
                                .jwt(b -> b.claim("realm_access",
                                        Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());  // 200, not 403
    }

    @Test
    @DisplayName("CUSTOMER role cannot access admin-only endpoints (403)")
    void customerRole_cannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(jwt()
                                .jwt(b -> b.claim("realm_access",
                                        Map.of("roles", List.of("CUSTOMER"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Token with no roles returns 403 on secured endpoint")
    void noRoles_returns403() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(jwt().jwt(b -> b.subject("no-role-user"))))
                .andExpect(status().isForbidden());
    }

    // ── Token claim validation ────────────────────────────────────────────────

    @Test
    @DisplayName("JWT with correct Keycloak realm_access structure is accepted")
    void keycloakRealmAccessClaim_accepted() throws Exception {
        // This reproduces the exact claim structure Keycloak emits:
        //   "realm_access": { "roles": ["ADMIN", "offline_access", "uma_authorization"] }
        mockMvc.perform(get("/api/orders/" + UUID.randomUUID())
                        .with(jwt()
                                .jwt(b -> b
                                        .subject("keycloak-user-id")
                                        .claim("preferred_username", "john.doe")
                                        .claim("email", "john@example.com")
                                        .claim("realm_access", Map.of(
                                                "roles", List.of("ADMIN", "offline_access")
                                        ))
                                )
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound()); // 404 (order not found), not 401/403
    }
}
