package com.example.ordermanagement.order;

import java.util.UUID;

public interface DeleteOrderUseCase {

    void deleteById(UUID orderId);
}
