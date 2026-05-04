package com.example.ordermanagement.integration.web;

import com.example.ordermanagement.BaseIntegrationTest;
import com.example.ordermanagement.domain.port.out.PricingServicePort;
import com.example.ordermanagement.support.TestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests: HTTP → Controller → Service → Repository → embedded PostgreSQL.
 *
 * PricingServicePort is mocked with @MockitoBean (Spring Boot 4 replacement for @MockBean)
 * because the external pricing REST call is covered separately in PricingServiceIT.
 *
 * JWT authentication uses Spring Security Test's jwt() post-processor — no real Keycloak needed.
 */
class OrderControllerIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // MockMvcTester — new fluent API introduced in Spring Boot 3.4 / 4.x
    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private ObjectMapper objectMapper;

    // @MockitoBean replaces @MockBean in Spring Boot 4
    @MockitoBean
    private PricingServicePort pricingServicePort;

    // ── CREATE ORDER ────────────────────────────────────────────────────────

    @Test
    void createOrder_withValidRequest_returns201AndOrderBody() throws Exception {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(new BigDecimal("59.97"));

        var request = TestDataBuilder.aCreateOrderRequest();

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .with(jwt().jwt(j -> j
                                .subject("user-123")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value("user-123"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalPrice").value(59.97))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].productId").value("PROD-1"));
    }

    @Test
    void createOrder_withInvalidRequest_returns400WithViolations() throws Exception {
        // Empty items list violates @NotEmpty
        var invalidRequest = new com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateOrderRequest(List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest))
                        .with(jwt().jwt(j -> j
                                .subject("user-123")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void createOrder_withoutAuthentication_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataBuilder.aCreateOrderRequest())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createOrder_withWrongRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataBuilder.aCreateOrderRequest()))
                        // "viewer" role is not authorised to create orders
                        .with(jwt().jwt(j -> j
                                .subject("user-123")
                                .claim("realm_access", Map.of("roles", List.of("viewer"))))))
                .andExpect(status().isForbidden());
    }

    // ── GET ORDER ───────────────────────────────────────────────────────────

    @Test
    void getOrder_existingOrder_returns200() throws Exception {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(new BigDecimal("29.98"));

        // First create an order to have a real persisted ID
        var createResponse = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataBuilder.aCreateOrderRequest()))
                        .with(jwt().jwt(j -> j.subject("user-456")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isCreated())
                .andReturn();

        String orderId = objectMapper.readTree(createResponse.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(jwt().jwt(j -> j.subject("user-456")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.customerId").value("user-456"));
    }

    @Test
    void getOrder_nonExistentOrder_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", "00000000-0000-0000-0000-000000000000")
                        .with(jwt().jwt(j -> j.subject("user-123")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order Not Found"));
    }

    // ── CONFIRM ORDER ───────────────────────────────────────────────────────

    @Test
    void confirmOrder_asAdmin_returns200WithConfirmedStatus() throws Exception {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(new BigDecimal("59.97"));

        // Create order first
        var createResponse = mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TestDataBuilder.aCreateOrderRequest()))
                        .with(jwt().jwt(j -> j.subject("user-789")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andReturn();

        String orderId = objectMapper.readTree(createResponse.getResponse().getContentAsString())
                .get("id").asText();

        // Confirm as admin
        mockMvc.perform(patch("/api/v1/orders/{id}/confirm", orderId)
                        .with(jwt().jwt(j -> j.subject("admin-1")
                                .claim("realm_access", Map.of("roles", List.of("user", "admin"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmOrder_asUser_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{id}/confirm", "00000000-0000-0000-0000-000000000001")
                        .with(jwt().jwt(j -> j.subject("user-123")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isForbidden());
    }

    // ── FLUENT MockMvcTester API (new in Spring Boot 4) ────────────────────

    @Test
    void getMyOrders_returnsOnlyCurrentUserOrders_usingMvcTester() {
        when(pricingServicePort.calculateTotalPrice(any())).thenReturn(BigDecimal.TEN);

        // MockMvcTester uses AssertJ fluent assertions — no checked exceptions
        assertThat(mvc.get().uri("/api/v1/orders")
                .with(jwt().jwt(j -> j.subject("user-tester")
                        .claim("realm_access", Map.of("roles", List.of("user"))))))
                .hasStatus2xxSuccessful()
                .bodyJson()
                .isArray();
    }
}
