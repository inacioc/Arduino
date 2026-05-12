package com.example.ordermanagement.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Order aggregate root.
 * All state transitions are expressed as domain methods to keep
 * business rules in one place and out of application/infrastructure layers.
 */
public class Order {

    private UUID id;
    private String customerId;
    private List<OrderItem> items;
    private OrderStatus status;
    private BigDecimal totalAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Order() {}

    // ── Factory method (new orders) ──────────────────────────────────────────

    public static Order create(String customerId, List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order must have at least one item");
        }
        Order order = new Order();
        order.id          = UUID.randomUUID();
        order.customerId  = customerId;
        order.items       = new ArrayList<>(items);
        order.status      = OrderStatus.PENDING;
        order.totalAmount = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.createdAt   = LocalDateTime.now();
        order.updatedAt   = LocalDateTime.now();
        return order;
    }

    // ── Reconstitution from persistence ──────────────────────────────────────

    public static Order reconstitute(UUID id, String customerId, List<OrderItem> items,
                                     OrderStatus status, BigDecimal totalAmount,
                                     LocalDateTime createdAt, LocalDateTime updatedAt) {
        Order order = new Order();
        order.id          = id;
        order.customerId  = customerId;
        order.items       = new ArrayList<>(items);
        order.status      = status;
        order.totalAmount = totalAmount;
        order.createdAt   = createdAt;
        order.updatedAt   = updatedAt;
        return order;
    }

    // ── State transitions ─────────────────────────────────────────────────────

    public void confirm() {
        requireStatus(OrderStatus.PENDING, "confirm");
        this.status    = OrderStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    public void startProcessing() {
        requireStatus(OrderStatus.CONFIRMED, "start processing");
        this.status    = OrderStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        requireStatus(OrderStatus.PROCESSING, "complete");
        this.status    = OrderStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        if (this.status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed order");
        }
        this.status    = OrderStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireStatus(OrderStatus expected, String action) {
        if (this.status != expected) {
            throw new IllegalStateException(
                "Cannot %s order in status %s".formatted(action, this.status));
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public UUID getId()               { return id; }
    public String getCustomerId()     { return customerId; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public OrderStatus getStatus()    { return status; }
    public BigDecimal getTotalAmount(){ return totalAmount; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public LocalDateTime getUpdatedAt(){ return updatedAt; }
}
