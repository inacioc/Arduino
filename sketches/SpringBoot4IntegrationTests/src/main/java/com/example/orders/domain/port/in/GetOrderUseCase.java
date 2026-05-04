package com.example.orders.domain.port.in;

import com.example.orders.domain.model.Order;

import java.util.Optional;

public interface GetOrderUseCase {
    Optional<Order> getOrder(String orderId);
}
