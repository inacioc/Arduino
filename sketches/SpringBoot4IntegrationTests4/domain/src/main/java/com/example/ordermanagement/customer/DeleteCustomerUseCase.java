package com.example.ordermanagement.customer;

import java.util.UUID;

public interface DeleteCustomerUseCase {

    void deleteById(UUID customerId);
}
