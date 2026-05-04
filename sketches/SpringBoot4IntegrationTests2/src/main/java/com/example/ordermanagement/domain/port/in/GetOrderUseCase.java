package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetOrderUseCase {

    Optional<Order> getOrderById(UUID orderId);

    List<Order> getOrdersByCustomer(String customerId);
}
