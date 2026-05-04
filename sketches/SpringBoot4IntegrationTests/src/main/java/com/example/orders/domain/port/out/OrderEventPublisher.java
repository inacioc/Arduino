package com.example.orders.domain.port.out;

import com.example.orders.domain.model.Order;

public interface OrderEventPublisher {
    void publishOrderCreated(Order order);
}
