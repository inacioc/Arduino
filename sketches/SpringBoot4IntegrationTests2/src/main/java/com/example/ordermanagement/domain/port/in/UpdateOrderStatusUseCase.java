package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Order;

import java.util.UUID;

public interface UpdateOrderStatusUseCase {

    Order confirmOrder(UUID orderId);

    Order cancelOrder(UUID orderId);
}
