package com.example.ordermanagement.domain.port.in;

import java.util.UUID;

public interface ProcessOrderUseCase {

    OrderResult confirmOrder(UUID orderId);

    OrderResult completeOrder(UUID orderId);

    OrderResult cancelOrder(UUID orderId);
}
