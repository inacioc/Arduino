package com.example.ordermanagement.domain.port.in;

import com.example.ordermanagement.domain.model.Customer;

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
