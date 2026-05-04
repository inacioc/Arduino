package com.example.ordermanagement.support;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.infrastructure.adapter.in.web.dto.CreateOrderRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fluent builder for test fixtures — keeps test code free of construction noise.
 */
public final class TestDataBuilder {

    public static final String DEFAULT_CUSTOMER_ID = "customer-abc-123";

    private TestDataBuilder() {}

    public static Order anOrder() {
        return Order.create(DEFAULT_CUSTOMER_ID, List.of(
                new OrderItem("PROD-1", "Widget A", 2, new BigDecimal("14.99")),
                new OrderItem("PROD-2", "Gadget B", 1, new BigDecimal("29.99"))
        ));
    }

    public static Order anOrderWithStatus(OrderStatus status) {
        Order order = anOrder();
        order.applyPricing(new BigDecimal("59.97"));
        return switch (status) {
            case CONFIRMED -> { order.confirm(); yield order; }
            case CANCELLED -> { order.cancel(); yield order; }
            default -> order;
        };
    }

    public static CreateOrderRequest aCreateOrderRequest() {
        return new CreateOrderRequest(List.of(
                new CreateOrderRequest.OrderItemRequest("PROD-1", "Widget A", 2),
                new CreateOrderRequest.OrderItemRequest("PROD-2", "Gadget B", 1)
        ));
    }

    public static CreateOrderRequest aCreateOrderRequestWith(String productId, int quantity) {
        return new CreateOrderRequest(List.of(
                new CreateOrderRequest.OrderItemRequest(productId, "Test Product", quantity)
        ));
    }

    /** Produces the JSON body for WireMock pricing response stubs. */
    public static String pricingResponseJson(String totalPrice) {
        return """
                {"totalPrice": %s, "currency": "USD"}
                """.formatted(totalPrice);
    }

    public static UUID randomOrderId() {
        return UUID.randomUUID();
    }
}
