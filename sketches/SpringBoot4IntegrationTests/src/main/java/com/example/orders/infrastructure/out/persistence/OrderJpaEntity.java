package com.example.orders.infrastructure.out.persistence;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class OrderJpaEntity {

    @Id
    @Column(nullable = false)
    private String id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatusJpa status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    protected OrderJpaEntity() {}

    public OrderJpaEntity(String id, String customerId, OrderStatusJpa status, LocalDateTime createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.createdAt = createdAt;
    }

    public void addItem(OrderItemJpaEntity item) {
        items.add(item);
    }

    public String getId() { return id; }
    public String getCustomerId() { return customerId; }
    public OrderStatusJpa getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<OrderItemJpaEntity> getItems() { return items; }

    public enum OrderStatusJpa {
        PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED
    }
}
