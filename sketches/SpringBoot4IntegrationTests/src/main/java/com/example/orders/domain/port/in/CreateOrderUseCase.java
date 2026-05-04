package com.example.orders.domain.port.in;

import com.example.orders.domain.model.Order;

public interface CreateOrderUseCase {
    Order createOrder(CreateOrderCommand command);
}
