package com.example.ordermanagement.order;

import com.example.ordermanagement.shared.ResourceNotFoundException;

/** No order exists with the given id. */
public class OrderNotFoundException extends ResourceNotFoundException {

    public OrderNotFoundException(String identifier) {
        super("Order", identifier);
    }
}
