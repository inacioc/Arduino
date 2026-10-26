package com.example.ordermanagement.order;

import com.example.ordermanagement.order.internal.domain.Order;

import java.util.UUID;

public interface ProcessOrderUseCase {

    Order confirmOrder(UUID orderId);

    Order completeOrder(UUID orderId);

    Order cancelOrder(UUID orderId);
}
