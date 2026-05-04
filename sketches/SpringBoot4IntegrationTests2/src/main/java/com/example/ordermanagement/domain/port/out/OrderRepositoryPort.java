package com.example.ordermanagement.domain.port.out;

import com.example.ordermanagement.domain.model.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryPort {

    Order save(Order order);

    Optional<Order> findById(UUID orderId);

    List<Order> findByCustomerId(String customerId);
}
