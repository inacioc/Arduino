package com.example.ordermanagement.order.internal.adapter.out.persistence;

import com.example.ordermanagement.order.internal.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByStatus(OrderStatus status);

    List<OrderEntity> findByCustomerId(UUID customerId);
}
