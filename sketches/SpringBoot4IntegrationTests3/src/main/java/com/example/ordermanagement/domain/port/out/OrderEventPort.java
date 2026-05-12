package com.example.ordermanagement.domain.port.out;

import com.example.ordermanagement.domain.model.Order;

public interface OrderEventPort {

    void publishOrderCreated(Order order);

    void publishOrderCompleted(Order order);
}
