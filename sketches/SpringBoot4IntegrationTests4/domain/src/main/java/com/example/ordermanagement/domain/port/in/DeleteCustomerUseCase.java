package com.example.ordermanagement.domain.port.in;

import java.util.UUID;

public interface DeleteCustomerUseCase {

    void deleteById(UUID customerId);
}
