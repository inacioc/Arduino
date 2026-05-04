package com.example.orders.infrastructure.out.client;

public class InventoryServiceException extends RuntimeException {

    public InventoryServiceException(String message) {
        super(message);
    }
}
