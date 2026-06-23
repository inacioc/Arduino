package com.example.ordermanagement.domain.port.in;

import java.math.BigDecimal;
import java.util.List;

public interface CreateOrderUseCase {

    OrderResult createOrder(CreateOrderCommand command);

    record CreateOrderCommand(
            String customerId,
            List<OrderItemCommand> items
    ) {}

    record OrderItemCommand(
            String productId,
            int quantity,
            BigDecimal unitPrice
    ) {}
}
