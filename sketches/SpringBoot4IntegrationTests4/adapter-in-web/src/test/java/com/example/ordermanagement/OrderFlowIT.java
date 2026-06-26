package com.example.ordermanagement;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.model.Product;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import com.example.ordermanagement.domain.port.out.ProductRepositoryPort;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateOrderRequest;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.OrderItemRequest;
import com.example.ordermanagement.infrastructure.adapter.out.messaging.OrderEvent;
import com.example.ordermanagement.support.IntegrationTestBase;
import com.example.ordermanagement.support.JwtHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.context.jdbc.Sql;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test covering the full order lifecycle:
 *
 *   Customer creates order  (POST /api/orders)
 *       → ProductRepositoryPort mocked (domain port for the local catalogue)
 *       → persists to PostgreSQL
 *       → publishes ORDER_CREATED event to IBM MQ
 *
 *   Admin confirms order    (PUT /api/orders/{id}/confirm)
 *       → status: PENDING → CONFIRMED
 *
 *   Admin completes order   (PUT /api/orders/{id}/complete)
 *       → status: CONFIRMED → PROCESSING → COMPLETED
 *       → publishes ORDER_COMPLETED event to IBM MQ
 *
 * ProductRepositoryPort is mocked at the hexagonal port boundary.
 * The JPA behaviour of ProductPersistenceAdapter is tested separately.
 */
@Sql(scripts = "/sql/clean-orders.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class OrderFlowIT extends IntegrationTestBase {

    private static final UUID PROD_E2E = UUID.fromString("22222222-2222-2222-2222-2222222222e2");

    @MockBean
    private ProductRepositoryPort productRepository;

    @Autowired
    private OrderRepositoryPort orderRepository;

    @Autowired
    private JmsTemplate jmsTemplate;

    @Value("${app.messaging.order-events-queue}")
    private String orderEventsQueue;

    @BeforeEach
    void setup() {
        drainQueue();

        when(productRepository.findById(PROD_E2E))
                .thenReturn(Optional.of(Product.create(
                        PROD_E2E, "E2E Test Widget", new BigDecimal("100.00"), true)));
    }

    @Test
    @DisplayName("Full order lifecycle: create → confirm → complete with MQ events")
    void fullOrderLifecycle() throws Exception {

        // ── Step 1: Customer creates an order ─────────────────────────────────
        CreateOrderRequest createRequest = new CreateOrderRequest(
                "e2e-customer",
                List.of(new OrderItemRequest(PROD_E2E, 2, new BigDecimal("100.00")))
        );

        String createResponse = mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("e2e-customer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(200.00))
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID orderId = UUID.fromString(
                objectMapper.readTree(createResponse).get("id").asText());

        // Verify persisted in DB
        Order savedOrder = orderRepository.findById(orderId).orElseThrow();
        assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(savedOrder.getCustomerId()).isEqualTo("e2e-customer");
        assertThat(savedOrder.getItems()).hasSize(1);

        // Verify ORDER_CREATED event published to IBM MQ
        OrderEvent createdEvent = receiveEvent();
        assertThat(createdEvent).isNotNull();
        assertThat(createdEvent.eventType()).isEqualTo(OrderEvent.ORDER_CREATED);
        assertThat(createdEvent.orderId()).isEqualTo(orderId);

        // ── Step 2: Admin confirms the order ──────────────────────────────────
        mockMvc.perform(put("/api/orders/{id}/confirm", orderId)
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);

        // ── Step 3: Admin completes the order ─────────────────────────────────
        mockMvc.perform(put("/api/orders/{id}/complete", orderId)
                        .with(JwtHelper.adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.COMPLETED);

        // Verify ORDER_COMPLETED event published to IBM MQ
        OrderEvent completedEvent = receiveEvent();
        assertThat(completedEvent).isNotNull();
        assertThat(completedEvent.eventType()).isEqualTo(OrderEvent.ORDER_COMPLETED);
        assertThat(completedEvent.orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("Customer can view their own orders after creation")
    void customerCanViewOrders() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "view-customer",
                List.of(new OrderItemRequest(PROD_E2E, 1, new BigDecimal("100.00")))
        );

        mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("view-customer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/orders/customer/{customerId}", "view-customer")
                        .with(JwtHelper.customerToken("view-customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].customerId").value("view-customer"));
    }

    @Test
    @DisplayName("Order cancellation is reflected in DB")
    void cancelOrder_persistsCancelledStatus() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "cancel-customer",
                List.of(new OrderItemRequest(PROD_E2E, 1, new BigDecimal("100.00")))
        );

        String createResponse = mockMvc.perform(post("/api/orders")
                        .with(JwtHelper.customerToken("cancel-customer"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID orderId = UUID.fromString(
                objectMapper.readTree(createResponse).get("id").asText());

        mockMvc.perform(put("/api/orders/{id}/cancel", orderId)
                        .with(JwtHelper.customerToken("cancel-customer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drainQueue() {
        String msg;
        int maxAttempts = 20;
        do {
            msg = (String) jmsTemplate.receiveAndConvert(orderEventsQueue);
        } while (msg != null && --maxAttempts > 0);
    }

    private OrderEvent receiveEvent() throws Exception {
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            String raw = (String) jmsTemplate.receiveAndConvert(orderEventsQueue);
            if (raw != null) {
                return objectMapper.readValue(raw, OrderEvent.class);
            }
        }
        return null;
    }
}
