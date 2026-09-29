package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.model.Customer;
import com.example.ordermanagement.domain.port.in.GetCustomerUseCase;
import com.example.ordermanagement.domain.port.in.SaveCustomerUseCase;
import com.example.ordermanagement.domain.port.out.CustomerRepositoryPort;
import com.example.ordermanagement.domain.validation.SaveCustomerValidator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain service for customers. Mirrors {@link ProductDomainService}: it drives the
 * outbound {@link CustomerRepositoryPort} and returns the rich {@link Customer} aggregate
 * directly (pragmatic style) for the driving adapter to map at the edge.
 */
@Service
@Transactional
public class CustomerDomainService implements SaveCustomerUseCase, GetCustomerUseCase {

    private final CustomerRepositoryPort customerRepository;
    private final SaveCustomerValidator saveCustomerValidator;

    public CustomerDomainService(CustomerRepositoryPort customerRepository,
                                  SaveCustomerValidator saveCustomerValidator) {
        this.customerRepository = customerRepository;
        this.saveCustomerValidator = saveCustomerValidator;
    }

    // ── SaveCustomerUseCase ─────────────────────────────────────────────────────

    @Override
    public Customer save(SaveCustomerCommand command) {
        saveCustomerValidator.assertValid(command);

        Customer customer = Customer.create(
                command.firstName(),
                command.lastName(),
                command.telephone(),
                command.email()
        );
        return customerRepository.save(customer);
    }

    // ── GetCustomerUseCase ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findById(UUID customerId) {
        return customerRepository.findById(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return customerRepository.findAll();
    }
}
