package com.example.ordermanagement.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class Order {

    private final UUID id;
    private final String customerId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private BigDecimal totalPrice;
    private final Instant createdAt;

    private Order(UUID id, String customerId, List<OrderItem> items,
                  OrderStatus status, BigDecimal totalPrice, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.items = List.copyOf(items);
        this.status = status;
        this.totalPrice = totalPrice;
        this.createdAt = createdAt;
    }

    public static Order create(String customerId, List<OrderItem> items) {
        return new Order(UUID.randomUUID(), customerId, items,
                OrderStatus.PENDING, BigDecimal.ZERO, Instant.now());
    }

    /** Reconstitutes an Order from persistence — never call from domain logic. */
    public static Order reconstitute(UUID id, String customerId, List<OrderItem> items,
                                     OrderStatus status, BigDecimal totalPrice, Instant createdAt) {
        return new Order(id, customerId, items, status, totalPrice, createdAt);
    }

    public void applyPricing(BigDecimal price) {
        this.totalPrice = price;
    }

    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order can only be confirmed from PENDING state, current: " + status);
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        if (status == OrderStatus.DELIVERED) {
            throw new IllegalStateException("Delivered orders cannot be cancelled");
        }
        this.status = OrderStatus.CANCELLED;
    }

    public UUID getId() { return id; }
    public String getCustomerId() { return customerId; }
    public List<OrderItem> getItems() { return items; }
    public OrderStatus getStatus() { return status; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public Instant getCreatedAt() { return createdAt; }
}
