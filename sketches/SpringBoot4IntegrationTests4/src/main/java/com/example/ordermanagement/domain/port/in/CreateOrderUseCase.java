package com.example.ordermanagement.domain.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CreateOrderUseCase {

    OrderResult createOrder(CreateOrderCommand command);

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
