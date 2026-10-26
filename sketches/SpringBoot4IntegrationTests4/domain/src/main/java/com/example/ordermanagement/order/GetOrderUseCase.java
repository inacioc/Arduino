package com.example.ordermanagement.order;

import com.example.ordermanagement.order.internal.domain.Order;
import com.example.ordermanagement.order.internal.domain.OrderStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetOrderUseCase {

    Optional<Order> findById(UUID orderId);

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByCustomerId(UUID customerId);
}
