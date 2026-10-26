package com.example.ordermanagement.order;

import com.example.ordermanagement.order.internal.domain.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreateOrderUseCase {

    Order createOrder(CreateOrderCommand command);

    record CreateOrderCommand(
            UUID customerId,
            List<OrderItemCommand> items
    ) {}

    record OrderItemCommand(
            UUID productId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
