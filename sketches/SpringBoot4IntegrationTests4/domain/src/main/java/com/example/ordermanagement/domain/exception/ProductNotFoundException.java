package com.example.ordermanagement.domain.exception;

/** No product exists with the given id. */
public class ProductNotFoundException extends ResourceNotFoundException {

    public ProductNotFoundException(String identifier) {
        super("Product", identifier);
    }
}
