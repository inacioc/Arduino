package com.example.ordermanagement.domain.port.in;

import java.util.UUID;

public interface DeleteOrderUseCase {

    void deleteById(UUID orderId);
}
