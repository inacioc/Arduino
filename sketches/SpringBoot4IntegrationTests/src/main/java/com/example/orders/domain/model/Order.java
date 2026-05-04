package com.example.orders.domain.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Order {

    private final String id;
    private final String customerId;
    private final List<OrderItem> items;
    private OrderStatus status;
    private final LocalDateTime createdAt;

    private Order(String id, String customerId, List<OrderItem> items,
                  OrderStatus status, LocalDateTime createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.items = new ArrayList<>(items);
        this.status = status;
        this.createdAt = createdAt;
    }

    // Factory — creates a new order with a generated ID
    public static Order create(String customerId, List<OrderItem> items) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer ID cannot be blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
        return new Order(
                UUID.randomUUID().toString(),
                customerId,
                items,
                OrderStatus.PENDING,
                LocalDateTime.now()
        );
    }

    // Factory — reconstitutes an order from persistence
    public static Order reconstitute(String id, String customerId, List<OrderItem> items,
                                     OrderStatus status, LocalDateTime createdAt) {
        return new Order(id, customerId, items, status, createdAt);
    }

    public void confirm() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Only PENDING orders can be confirmed, current status: " + status);
        }
        this.status = OrderStatus.CONFIRMED;
    }

    public void cancel() {
        if (status == OrderStatus.DELIVERED || status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot cancel order with status: " + status);
        }
        this.status = OrderStatus.CANCELLED;
    }

    public Money total() {
        return items.stream()
                .map(OrderItem::subtotal)
                .reduce(Money.ZERO, Money::add);
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public OrderStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
