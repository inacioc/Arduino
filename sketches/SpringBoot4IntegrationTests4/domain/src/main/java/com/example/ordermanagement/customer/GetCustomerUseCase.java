package com.example.ordermanagement.customer;

import com.example.ordermanagement.customer.Customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetCustomerUseCase {

    Optional<Customer> findById(UUID customerId);

    /** The customer currently holding this email, if any - email is the business key. */
    Optional<Customer> findByEmail(String email);

    List<Customer> findAll();
}
