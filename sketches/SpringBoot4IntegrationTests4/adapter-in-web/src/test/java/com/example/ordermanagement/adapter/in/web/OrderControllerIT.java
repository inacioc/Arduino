package com.example.ordermanagement.adapter.in.web;

import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase.CreateOrderCommand;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.OrderResult;
import com.example.ordermanagement.domain.port.in.OrderResult.OrderItemResult;
import com.example.ordermanagement.domain.port.in.ProcessOrderUseCase;
import com.example.ordermanagement.domain.service.OrderDomainService.ProductNotAvailableException;
import com.example.ordermanagement.domain.service.OrderDomainService.ProductNotFoundException;
import com.example.ordermanagement.infrastructure.adapter.in.web.OrderController;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.OrderItemRequest;
import com.example.ordermanagement.infrastructure.config.SecurityConfig;
import com.example.ordermanagement.support.JwtHelper;
import com.example.ordermanagement.support.WebSecurityTestConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-slice tests for OrderController.
 *
 * @WebMvcTest boots only the MVC layer (controller + Jackson + security filters);
 * the inbound use-case ports are mocked, so no DB / MQ / batch is involved. This
 * tests HTTP routing, validation, auth and exception→status mapping in isolation.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, WebSecurityTestConfig.class})
class OrderControllerIT {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private CreateOrderUseCase createOrder;
    @MockBean private GetOrderUseCase getOrder;
    @MockBean private ProcessOrderUseCase processOrder;

    private static final UUID PRODUCT_ID = UUID.fromString("11111111-1111-1111-1111-111111111001");

    private static OrderResult orderResult(String status) {
        return new OrderResult(
                UUID.randomUUID(), "customer-1", status, new BigDecimal("99.98"),
                List.of(new OrderItemResult(PRODUCT_ID, "Widget Alpha", 2,
                        new BigDecimal("49.99"), new BigDecimal("99.98"))),
                LocalDateTime.now(), LocalDateTime.now());
    }

    private static CreateOrderRequest request(int quantity) {
        return new CreateOrderRequest("customer-1",
                List.of(new OrderItemRequest(PRODUCT_ID, quantity, new BigDecimal("49.99"))));
    }

    // ── POST /api/orders ──────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/orders - creates order and returns 201")
    void createOrder_success() throws Exception {
        when(createOrder.createOrder(any(CreateOrderCommand.class))).thenReturn(orderResult("PENDING"));

        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(2))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(99.98))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productId").value(PRODUCT_ID.toString()))
                .andExpect(jsonPath("$.items[0].productName").value("Widget Alpha"));
    }

    @Test
    @DisplayName("POST /api/orders - returns 401 when no JWT present")
    void createOrder_unauthorized() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(1))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/orders - returns 403 when ADMIN tries to create (wrong role)")
    void createOrder_forbidden_wrongRole() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(1))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/orders - returns 400 when request body is invalid")
    void createOrder_validationError() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(0))))  // quantity=0
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    @DisplayName("POST /api/orders - returns 422 when product is not available")
    void createOrder_productNotAvailable() throws Exception {
        when(createOrder.createOrder(any(CreateOrderCommand.class)))
                .thenThrow(new ProductNotAvailableException(PRODUCT_ID));

        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(1))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("POST /api/orders - returns 422 when product does not exist")
    void createOrder_productNotFound() throws Exception {
        when(createOrder.createOrder(any(CreateOrderCommand.class)))
                .thenThrow(new ProductNotFoundException(PRODUCT_ID));

        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("customer-1"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request(1))))
                .andExpect(status().isUnprocessableEntity());
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders/{id} - returns 200 with order when found")
    void getById_found() throws Exception {
        when(getOrder.findById(any(UUID.class))).thenReturn(Optional.of(orderResult("PENDING")));

        mockMvc.perform(get("/api/orders/{id}", UUID.randomUUID())
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("GET /api/orders/{id} - returns 404 when order does not exist")
    void getById_notFound() throws Exception {
        when(getOrder.findById(any(UUID.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/{id}", UUID.randomUUID())
                        .with(JwtHelper.customerToken("customer-1")))
                .andExpect(status().isNotFound());
    }

    // ── PUT /api/orders/{id}/confirm ──────────────────────────────────────────

    @Test
    @DisplayName("PUT /api/orders/{id}/confirm - confirms a PENDING order")
    void confirmOrder_success() throws Exception {
        when(processOrder.confirmOrder(any(UUID.class))).thenReturn(orderResult("CONFIRMED"));

        mockMvc.perform(put("/api/orders/{id}/confirm", UUID.randomUUID())
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("PUT /api/orders/{id}/confirm - returns 409 on illegal transition")
    void confirmOrder_conflict() throws Exception {
        when(processOrder.confirmOrder(any(UUID.class)))
                .thenThrow(new IllegalStateException("Cannot confirm order in status CONFIRMED"));

        mockMvc.perform(put("/api/orders/{id}/confirm", UUID.randomUUID())
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isConflict());
    }

    // ── GET /api/orders?status=PENDING ───────────────────────────────────────

    @Test
    @DisplayName("GET /api/orders?status=PENDING - lists orders (admin only)")
    void listByStatus_adminAllowed() throws Exception {
        when(getOrder.findByStatus(anyString())).thenReturn(List.of(orderResult("PENDING")));

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
