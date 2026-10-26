package com.example.ordermanagement.customer;

import com.example.ordermanagement.customer.Customer;

/**
 * Inbound port for registering customers. Returns the persisted {@link Customer}
 * aggregate (pragmatic style, mirrors {@code SaveProductUseCase}).
 */
public interface SaveCustomerUseCase {

    Customer save(SaveCustomerCommand command);

    record SaveCustomerCommand(
            String firstName,
            String lastName,
            String telephone,
            String email
    ) {}
}
