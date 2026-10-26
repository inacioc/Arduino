package com.example.ordermanagement.product;

import java.util.UUID;

public interface DeleteProductUseCase {

    void deleteById(UUID productId);
}
