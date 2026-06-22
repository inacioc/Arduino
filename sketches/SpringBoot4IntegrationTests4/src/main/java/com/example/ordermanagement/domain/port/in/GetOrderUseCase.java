package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Order;
import com.example.ordermanagement.domain.model.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetOrderUseCase {

    Optional<Order> findById(UUID orderId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByCustomerId(String customerId);
}
