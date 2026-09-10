package com.example.ordermanagement.domain.exception;

/** No order exists with the given id. */
public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(String identifier) {
        super("Order", identifier);
    }
}
