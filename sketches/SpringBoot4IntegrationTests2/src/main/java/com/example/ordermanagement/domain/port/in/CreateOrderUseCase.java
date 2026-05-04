package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Order;

import java.util.List;

public interface CreateOrderUseCase {

    Order createOrder(CreateOrderCommand command);

    record CreateOrderCommand(String customerId, List<OrderItemDto> items) {

        public record OrderItemDto(String productId, String productName, int quantity) {}
    }
}
