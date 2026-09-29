package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetCustomerUseCase {

    Optional<Customer> findById(UUID customerId);

    List<Customer> findAll();
}
