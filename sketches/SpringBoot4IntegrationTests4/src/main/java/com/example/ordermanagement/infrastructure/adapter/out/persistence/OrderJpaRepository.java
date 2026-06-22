package com.example.ordermanagement.infrastructure.adapter.out.persistence;

import com.example.ordermanagement.domain.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderEntity, UUID> {

    List<OrderEntity> findByStatus(OrderStatus status);

    List<OrderEntity> findByCustomerId(String customerId);
}
