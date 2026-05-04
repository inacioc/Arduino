package com.example.orders.domain.port.out;

public interface InventoryPort {
    /**
     * Checks whether the requested quantity of a product is available in the inventory service.
     */
    boolean isAvailable(String productId, int requestedQuantity);
}
