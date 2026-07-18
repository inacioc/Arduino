package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Order;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreateOrderUseCase {

    Order createOrder(CreateOrderCommand command);

    record CreateOrderCommand(
            String customerId,
            List<OrderItemCommand> items
    ) {}

    record OrderItemCommand(
            UUID productId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
