package com.example.orders.infrastructure.out.persistence;

import com.example.orders.domain.model.Money;
import com.example.orders.domain.model.Order;
import com.example.orders.domain.model.OrderItem;
import com.example.orders.domain.model.OrderStatus;
import com.example.orders.domain.port.out.OrderRepository;
import com.example.orders.infrastructure.out.persistence.OrderJpaEntity.OrderStatusJpa;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class OrderPersistenceAdapter implements OrderRepository {

    private final SpringDataOrderRepository jpaRepository;

    public OrderPersistenceAdapter(SpringDataOrderRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Order save(Order order) {
        OrderJpaEntity entity = toEntity(order);
        OrderJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Order> findById(String orderId) {
        return jpaRepository.findById(orderId).map(this::toDomain);
    }

    private OrderJpaEntity toEntity(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity(
                order.getId(),
                order.getCustomerId(),
                OrderStatusJpa.valueOf(order.getStatus().name()),
                order.getCreatedAt()
        );
        order.getItems().forEach(item -> entity.addItem(
                new OrderItemJpaEntity(
                        item.productId(),
                        item.productName(),
                        item.quantity(),
                        item.unitPrice().amount(),
                        item.unitPrice().currency(),
                        entity
                )
        ));
        return entity;
    }

    private Order toDomain(OrderJpaEntity entity) {
        List<OrderItem> items = entity.getItems().stream()
                .map(i -> new OrderItem(
                        i.getProductId(),
                        i.getProductName(),
                        i.getQuantity(),
                        Money.of(i.getUnitPrice(), i.getCurrency())
                ))
                .toList();

        return Order.reconstitute(
                entity.getId(),
                entity.getCustomerId(),
                items,
                OrderStatus.valueOf(entity.getStatus().name()),
                entity.getCreatedAt()
        );
    }
}
