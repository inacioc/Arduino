package com.example.ordermanagement.product;

import com.example.ordermanagement.shared.ResourceNotFoundException;

/** No product exists with the given id. */
public class ProductNotFoundException extends ResourceNotFoundException {

    public ProductNotFoundException(String identifier) {
        super("Product", identifier);
    }
}
