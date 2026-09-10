package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.port.out.OrderRepositoryPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderPersistenceAdapter extends AbstractPersistenceAdapter implements OrderRepositoryPort {

    private final OrderJpaRepository jpaRepository;
    private final OrderEntityMapper mapper;

    public OrderPersistenceAdapter(OrderJpaRepository jpaRepository, OrderEntityMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper        = mapper;
    }

    @Override
    public Order save(Order order) {
        // No business duplicate is possible here - Order.create() always assigns a fresh
        // server-generated UUID, so a save() can never collide with an existing order the
        // way ProductPersistenceAdapter.save() can on name. The 3-arg overload is used
        // deliberately: there is no entity-specific "already exists" exception to build.
        return executeAndTranslate(() -> {
            OrderEntity entity = mapper.toEntity(order);

            // Rebuild items and set back-reference
            entity.getItems().clear();
            order.getItems().forEach(item -> {
                OrderItemEntity itemEntity = mapper.toItemEntity(item);
                itemEntity.setOrder(entity);
                entity.getItems().add(itemEntity);
            });

            return mapper.toDomain(jpaRepository.save(entity));
        }, "Order", order.getId().toString());
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return executeAndTranslate(
                () -> jpaRepository.findById(id).map(mapper::toDomain),
                "Order", id.toString());
    }

    @Override
    public List<Order> findByStatus(OrderStatus status) {
        return executeAndTranslate(
                () -> jpaRepository.findByStatus(status).stream().map(mapper::toDomain).toList(),
                "Order", "status=" + status);
    }

    @Override
    public List<Order> findByCustomerId(String customerId) {
        return executeAndTranslate(
                () -> jpaRepository.findByCustomerId(customerId).stream().map(mapper::toDomain).toList(),
                "Order", "customerId=" + customerId);
    }

    @Override
    public void deleteById(UUID id) {
        executeAndTranslate(() -> {
            jpaRepository.deleteById(id);
            return null;
        }, "Order", id.toString());
    }
}
