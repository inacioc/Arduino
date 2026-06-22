package com.example.ordermanagement.adapter.in.web;

import com.example.ordermanagement.domain.port.out.ProductServicePort;
import com.example.ordermanagement.domain.port.out.ProductServicePort.ProductInfo;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.OrderItemRequest;
import com.example.ordermanagement.support.IntegrationTestBase;
import com.example.ordermanagement.support.JwtHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OrderController.
 *
 * Strategy:
 *  - Full Spring context (@SpringBootTest via IntegrationTestBase).
 *  - Real PostgreSQL database (application-test.yml).
 *  - Keycloak mocked via SecurityMockMvcRequestPostProcessors.jwt().
 *  - ProductServicePort mocked with @MockBean — the controller tests verify
 *    HTTP routing, validation, auth, and domain logic. They are not responsible
 *    for testing the HTTP client that calls the product service; that is
 *    ProductRestAdapterIT's job.
 *  - @Sql scripts clean DB state before each test.
 */
@Sql(scripts = "/sql/clean-orders.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderControllerIT extends IntegrationTestBase {

    @MockBean
    private ProductServicePort productServicePort;

    private static final String PRODUCT_ID        = "PROD-001";
    private static final String PENDING_ORDER_ID  = "aaaaaaaa-0000-0000-0000-000000000001";
    private static final String CONFIRMED_ORDER_ID = "aaaaaaaa-0000-0000-0000-000000000002";

    @BeforeEach
    void stubProductService() {
        when(productServicePort.findProduct(PRODUCT_ID))
                .thenReturn(Optional.of(new ProductInfo(
                        PRODUCT_ID, "Widget Alpha", new BigDecimal("49.99"), true)));

        when(productServicePort.findProduct("PROD-UNAVAILABLE"))
                .thenReturn(Optional.of(new ProductInfo(
                        "PROD-UNAVAILABLE", "Out of Stock", new BigDecimal("10.00"), false)));

        when(productServicePort.findProduct("PROD-UNKNOWN"))
                .thenReturn(Optional.empty());
    }

    // ── POST /api/orders ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders - creates order and returns 201")
    void createOrder_success() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderItemRequest(PRODUCT_ID, 2, new BigDecimal("49.99")))
        );

        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value("customer-1"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(99.98))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID))
                .andExpect(jsonPath("$.items[0].productName").value("Widget Alpha"));
    }

    @Test
    @DisplayName("POST /api/orders - returns 401 when no JWT present")
    void createOrder_unauthorized() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderItemRequest(PRODUCT_ID, 1, new BigDecimal("49.99")))
        );

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/orders - returns 403 when ADMIN tries to create (wrong role)")
    void createOrder_forbidden_wrongRole() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderItemRequest(PRODUCT_ID, 1, new BigDecimal("49.99")))
        );

        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/orders - returns 400 when request body is invalid")
    void createOrder_validationError() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderItemRequest(PRODUCT_ID, 0, new BigDecimal("49.99")))  // quantity=0 invalid
        );

        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /api/orders - returns 422 when product is not available")
    void createOrder_productNotAvailable() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderItemRequest("PROD-UNAVAILABLE", 1, new BigDecimal("10.00")))
        );

        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /api/orders - returns 422 when product does not exist")
    void createOrder_productNotFound() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "customer-1",
                List.of(new OrderItemRequest("PROD-UNKNOWN", 1, new BigDecimal("10.00")))
        );

        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{id} - returns 200 with order when found")
    @Sql(scripts = {"/sql/clean-orders.sql", "/sql/insert-test-orders.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void getById_found() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", PENDING_ORDER_ID)
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(PENDING_ORDER_ID))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.customerId").value("customer-1"))
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("GET /api/orders/{id} - returns 404 when order does not exist")
    void getById_notFound() throws Exception {
        mockMvc.perform(get("/api/orders/{id}", UUID.randomUUID())
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/orders/{id}/confirm ──────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/orders/{id}/confirm - confirms a PENDING order")
    @Sql(scripts = {"/sql/clean-orders.sql", "/sql/insert-test-orders.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void confirmOrder_success() throws Exception {
        mockMvc.perform(put("/api/orders/{id}/confirm", PENDING_ORDER_ID)
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("PUT /api/orders/{id}/confirm - returns 409 when order is already CONFIRMED")
    @Sql(scripts = {"/sql/clean-orders.sql", "/sql/insert-test-orders.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void confirmOrder_conflict() throws Exception {
        mockMvc.perform(put("/api/orders/{id}/confirm", CONFIRMED_ORDER_ID)
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isConflict());
    }

    // ── GET /api/orders?status=PENDING ───────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders?status=PENDING - lists only PENDING orders (admin only)")
    @Sql(scripts = {"/sql/clean-orders.sql", "/sql/insert-test-orders.sql"},
         executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    void listByStatus_adminAllowed() throws Exception {
        mockMvc.perform(get("/api/orders").param("status", "PENDING")
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/orders - returns 403 for CUSTOMER role")
    void listByStatus_customerForbidden() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isForbidden());
    }
}
