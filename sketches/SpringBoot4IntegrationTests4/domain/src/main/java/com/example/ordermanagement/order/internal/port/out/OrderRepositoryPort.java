package com.example.ordermanagement.order.internal.port.out;

import com.example.ordermanagement.order.internal.domain.Order;
import com.example.ordermanagement.order.internal.domain.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByCustomerId(UUID customerId);

    void deleteById(UUID id);
}
