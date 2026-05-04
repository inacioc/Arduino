package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderItem;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import com.example.ordermanagement.infrastructure.adapter.out.persistence.entity.OrderItemJpaEntity;
import com.example.ordermanagement.infrastructure.adapter.out.persistence.entity.OrderJpaEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderPersistenceAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository jpaRepository;

    public OrderPersistenceAdapter(OrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        return toDomain(jpaRepository.save(toEntity(order)));
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return jpaRepository.findById(orderId).map(this::toDomain);
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        return jpaRepository.findByCustomerId(customerId).stream().map(this::toDomain).toList();
    }

    private OrderJpaEntity toEntity(Order order) {
        var entity = new OrderJpaEntity();
        entity.setId(order.getId());
        entity.setCustomerId(order.getCustomerId());
        entity.setStatus(order.getStatus());
        entity.setTotalPrice(order.getTotalPrice());
        entity.setCreatedAt(order.getCreatedAt());
        entity.setItems(order.getItems().stream().map(this::toItemEntity).toList());
        return entity;
    }

    private OrderItemJpaEntity toItemEntity(OrderItem item) {
        var entity = new OrderItemJpaEntity();
        entity.setProductId(item.productId());
        entity.setProductName(item.productName());
        entity.setQuantity(item.quantity());
        entity.setUnitPrice(item.unitPrice());
        return entity;
    }

    private Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                .toList();
        return Order.reconstitute(entity.getId(), entity.getCustomerId(), items,
                entity.getStatus(), entity.getTotalPrice(), entity.getCreatedAt());
    }
}
