package com.example.ordermanagement.domain.exception;

/** No customer exists with the given id. */
public class CustomerNotFoundException extends ResourceNotFoundException {

    public CustomerNotFoundException(String identifier) {
        super("Customer", identifier);
    }
}
