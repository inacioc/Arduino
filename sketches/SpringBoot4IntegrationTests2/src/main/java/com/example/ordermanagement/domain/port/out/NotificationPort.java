package com.example.ordermanagement.domain.port.out;

import com.example.ordermanagement.domain.model.Order;

public interface NotificationPort {

    void sendOrderCreatedNotification(Order order);

    void sendOrderStatusChangedNotification(Order order);
}
