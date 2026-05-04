package com.example.ordermanagement.integration.web;

import com.example.ordermanagement.application.service.OrderNotFoundException;
import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.in.CreateOrderUseCase;
import com.example.ordermanagement.domain.port.in.GetOrderUseCase;
import com.example.ordermanagement.domain.port.in.UpdateOrderStatusUseCase;
import com.example.ordermanagement.infrastructure.adapter.in.web.GlobalExceptionHandler;
import com.example.ordermanagement.infrastructure.adapter.in.web.OrderController;
import com.example.ordermanagement.infrastructure.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test: @WebMvcTest loads ONLY the web layer (controller + security + exception handler).
 * All use-case ports are mocked — no DB, no JMS, no external calls.
 * Fastest integration test tier; ideal for HTTP contract and security rule verification.
 */
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class OrderControllerSliceIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateOrderUseCase createOrderUseCase;

    @MockitoBean
    private GetOrderUseCase getOrderUseCase;

    @MockitoBean
    private UpdateOrderStatusUseCase updateOrderStatusUseCase;

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    void createOrder_delegatesToUseCase_andReturns201() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order stubbedOrder = Order.reconstitute(orderId, "user-abc",
                List.of(new OrderItem("P1", "Widget", 1, BigDecimal.TEN)),
                OrderStatus.PENDING, BigDecimal.TEN, java.time.Instant.now());

        when(createOrderUseCase.createOrder(any())).thenReturn(stubbedOrder);

        var body = """
                {"items": [{"productId": "P1", "productName": "Widget", "quantity": 1}]}
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(j -> j
                                .subject("user-abc")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void getOrder_whenFound_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order stubbedOrder = Order.reconstitute(orderId, "user-abc",
                List.of(new OrderItem("P1", "Widget", 1, BigDecimal.TEN)),
                OrderStatus.CONFIRMED, new BigDecimal("10.00"), java.time.Instant.now());

        when(getOrderUseCase.getOrderById(orderId)).thenReturn(Optional.of(stubbedOrder));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(jwt().jwt(j -> j.subject("user-abc")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void getOrder_whenNotFound_returns404WithProblemDetail() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(getOrderUseCase.getOrderById(orderId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .with(jwt().jwt(j -> j.subject("u")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isNotFound());
    }

    // ── Security rules ──────────────────────────────────────────────────────

    @Test
    void confirmOrder_whenUserRole_returns403() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{id}/confirm", UUID.randomUUID())
                        .with(jwt().jwt(j -> j.subject("user")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isForbidden());
    }

    @Test
    void confirmOrder_whenAdminRole_returns200() throws Exception {
        UUID orderId = UUID.randomUUID();
        Order confirmed = Order.reconstitute(orderId, "user-abc",
                List.of(new OrderItem("P1", "Widget", 1, BigDecimal.TEN)),
                OrderStatus.CONFIRMED, BigDecimal.TEN, java.time.Instant.now());

        when(updateOrderStatusUseCase.confirmOrder(orderId)).thenReturn(confirmed);

        mockMvc.perform(patch("/api/v1/orders/{id}/confirm", orderId)
                        .with(jwt().jwt(j -> j.subject("admin")
                                .claim("realm_access", Map.of("roles", List.of("user", "admin"))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void confirmOrder_whenOrderNotFound_returns404() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(updateOrderStatusUseCase.confirmOrder(orderId))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(patch("/api/v1/orders/{id}/confirm", orderId)
                        .with(jwt().jwt(j -> j.subject("admin")
                                .claim("realm_access", Map.of("roles", List.of("user", "admin"))))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Order Not Found"));
    }

    @Test
    void cancelOrder_alreadyDelivered_returns409() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(updateOrderStatusUseCase.cancelOrder(orderId))
                .thenThrow(new IllegalStateException("Delivered orders cannot be cancelled"));

        mockMvc.perform(patch("/api/v1/orders/{id}/cancel", orderId)
                        .with(jwt().jwt(j -> j.subject("admin")
                                .claim("realm_access", Map.of("roles", List.of("user", "admin"))))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid Order State Transition"));
    }

    // ── Validation ──────────────────────────────────────────────────────────

    @Test
    void createOrder_quantityZero_returns400() throws Exception {
        var body = """
                {"items": [{"productId": "P1", "productName": "Widget", "quantity": 0}]}
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(j -> j.subject("user")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    void createOrder_missingProductId_returns400() throws Exception {
        var body = """
                {"items": [{"productName": "Widget", "quantity": 1}]}
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(jwt().jwt(j -> j.subject("user")
                                .claim("realm_access", Map.of("roles", List.of("user"))))))
                .andExpect(status().isBadRequest());
    }
}
