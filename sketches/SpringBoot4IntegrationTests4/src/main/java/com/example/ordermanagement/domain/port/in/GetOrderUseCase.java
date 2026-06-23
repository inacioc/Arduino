package com.example.ordermanagement.domain.port.in;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetOrderUseCase {

    Optional<OrderResult> findById(UUID orderId);

    List<OrderResult> findByStatus(String status);

    List<OrderResult> findByCustomerId(String customerId);
}
