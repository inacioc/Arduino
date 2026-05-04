package com.example.orders.domain.port.out;

import com.example.orders.domain.model.Order;

import java.util.Optional;

public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(String orderId);
}
