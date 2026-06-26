package com.example.ordermanagement.domain.port.out;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(UUID id);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByCustomerId(String customerId);

    void deleteById(UUID id);
}
