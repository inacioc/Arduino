package com.example.ordermanagement.order.internal.port.out;

import com.example.ordermanagement.order.internal.domain.Order;

public interface OrderEventPort {

    void publishOrderCreated(Order order);

    void publishOrderCompleted(Order order);
}
