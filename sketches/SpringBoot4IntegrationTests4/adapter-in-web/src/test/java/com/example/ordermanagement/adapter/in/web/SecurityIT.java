package com.example.ordermanagement.adapter.in.web;

import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.ProcessOrderUseCase;
import com.example.ordermanagement.infrastructure.adapter.in.web.OrderController;
import com.example.ordermanagement.infrastructure.config.SecurityConfig;
import com.example.ordermanagement.support.WebSecurityTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Documents the HTTP security rules enforced by SecurityConfig, as a web slice.
 * Keycloak is mocked via {@code jwt()} (no JWKS / token endpoint calls).
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, WebSecurityTestConfig.class})
class SecurityIT {

    @Autowired private MockMvc mockMvc;

    @MockBean private CreateOrderUseCase createOrder;
    @MockBean private GetOrderUseCase getOrder;
    @MockBean private ProcessOrderUseCase processOrder;

    @Test
    @DisplayName("Any endpoint returns 401 with no Authorization header")
    void anyEndpoint_noAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/orders/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ADMIN role can access admin-only endpoints")
    void adminRole_canAccessAdminEndpoints() throws Exception {
        when(getOrder.findByStatus(anyString())).thenReturn(List.of());

        mockMvc.perform(get("/api/orders")
                        .with(jwt()
                                .jwt(b -> b.claim("realm_access", Map.of("roles", List.of("ADMIN"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("CUSTOMER role cannot access admin-only endpoints (403)")
    void customerRole_cannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(jwt()
                                .jwt(b -> b.claim("realm_access", Map.of("roles", List.of("CUSTOMER"))))
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

    @Test
    @DisplayName("JWT with correct Keycloak realm_access structure is accepted")
    void keycloakRealmAccessClaim_accepted() throws Exception {
        when(getOrder.findById(org.mockito.ArgumentMatchers.any(UUID.class)))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/" + UUID.randomUUID())
                        .with(jwt()
                                .jwt(b -> b
                                        .subject("keycloak-user-id")
                                        .claim("preferred_username", "john.doe")
                                        .claim("realm_access", Map.of(
                                                "roles", List.of("ADMIN", "offline_access"))))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isNotFound()); // 404 (not found), not 401/403
    }
}
