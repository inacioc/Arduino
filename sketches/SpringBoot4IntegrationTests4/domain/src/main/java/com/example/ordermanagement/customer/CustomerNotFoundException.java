package com.example.ordermanagement.customer;

import com.example.ordermanagement.shared.ResourceNotFoundException;

/** No customer exists with the given id. */
public class CustomerNotFoundException extends ResourceNotFoundException {

    public CustomerNotFoundException(String identifier) {
        super("Customer", identifier);
    }
}
