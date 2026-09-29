package com.example.ordermanagement.domain.exception;

/**
 * A customer with this email already exists. Mirrors {@code ProductAlreadyExistsException}:
 * {@code email} (not {@code id}) is the business key here, enforced by a unique constraint
 * (migration {@code V5}) and translated up from the resulting constraint violation by
 * {@code AbstractPersistenceAdapter}.
 */
public class CustomerAlreadyExistsException extends ResourceAlreadyExistsException {

    public CustomerAlreadyExistsException(String email) {
        super("Customer", email);
    }
}
