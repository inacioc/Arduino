package com.example.ordermanagement.domain.port.out;

import com.example.ordermanagement.domain.model.Customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for persisting customers. Like {@link ProductRepositoryPort} and
 * {@link OrderRepositoryPort}, this is a driven port owned by the domain, so it speaks
 * the domain language ({@link Customer}).
 */
public interface CustomerRepositoryPort {

    Customer save(Customer customer);

    Optional<Customer> findById(UUID id);

    /** The customer currently holding this email, if any — email is the business key. */
    Optional<Customer> findByEmail(String email);

    List<Customer> findAll();

    void deleteById(UUID id);
}
